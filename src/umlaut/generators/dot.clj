(ns umlaut.generators.dot
  (:require [clojure.java.io :as io]
            [umlaut.models :as model]
            [umlaut.core :as core]
            [umlaut.utils :refer :all]
            [clojure.spec :as s]
            [clojure.string :as string]
            [clojure.spec.test :as stest]))
(use '[clojure.java.shell :only [sh]])
(use '[clojure.pprint :only [pprint]])

(def header (slurp (io/resource "templates/header.dot")))

(def footer (slurp (io/resource "templates/footer.dot")))

;; Store all edges added in a subgraph, so we don't have redundant edges
(def ^:private edges (atom []))

(defn- arity-label
  "Builds the arity representation in the diagram"
  [[from to]]
  (if (= from to) (str "") (str "[" from ".." to "]")))

(defn- required-label [type-obj]
  "Checkes required attribute and returns ? or not"
  (if (type-obj :required)
    ""
    "?"))

(defn- type-label [type]
  "Receives a type object and builds a string"
  (str (type :type-id) (arity-label (type :arity)) (required-label type)))

(defn- attributes-list
  [{:keys [attributes]}]
  (reduce #(str %1 (:id %2) ": " (type-label %2) "\\l") "" attributes))

(defn- values-list
  [{:keys [values]}]
  (reduce #(str %1 %2 "\\l" "" values)))

(defn- node-label
  "Builds the string regarding object type"
  [kind type-obj]
  (case kind
    :type (str (:id type-obj) "|" (attributes-list type-obj))
    :interface (str "\\<\\<interface\\>\\>" (:id type-obj) "|" (attributes-list type-obj))
    :enum (str "\\<\\<enum\\>\\>" (:id type-obj) "|" (values-list type-obj))
    ""))

(defn- method-args-label [method]
  "Iterates over all the method arguments and builds the string"
  (->> (method :params)
    (map #(str (%1 :id) ": " (type-label %1)))
    (string/join ", ")))

(defn- method-label [method]
  "Builds the string for a method inside a type"
  (str (method :id) "(" (method-args-label method) "): " (type-label (method :return)) "\\l"))

(defn- node-methods [kind type-obj]
  "Iterates over all the methods building each one of the strings"
  (reduce
    (fn [acc method]
      (str acc (method-label method)))
    "" (type-obj :methods)))

(defn- node-str
  "Receives an AST node and generates the dotstring of the node"
  [[kind type-obj]]
  (str "  " (:id type-obj)
    " [label = \"{"
    (node-label kind type-obj)
    (node-methods kind type-obj)
    "}\"]"))

(defn- gen-subgraph-content
  [umlaut]
  (reduce (fn [acc [id node]]
            (str acc (node-str node) "\n")) "" (seq (umlaut :nodes))))

(defn- subgraph-id [umlaut]
  (->> (seq (umlaut :nodes))
       (first)
       (first)))

(defn gen-subgraph
  [umlaut]
  (let [ns-id (subgraph-id umlaut)]
   (str "subgraph "
        ns-id
        " {\n  label = \""
        ns-id
        "\"\n"
        (gen-subgraph-content umlaut)
        "}\n")))

(defn- get-group-set
  "Receives a umlaut structure and returns a set with all the boxes that will be drawn"
  [umlaut]
  (if (umlaut :current)
    (set (flatten ((second (umlaut :current)) :groups)))
    (set [])))

(defn- draw-edge? [node umlaut]
  (or
    (contains? (get-group-set umlaut) (node :type-id))
    (in? (node :type-id) (keys (umlaut :nodes)))))

(defn- filter-attr-in-map
  "Filter attributes of a declaration block that are inside any of the groups"
  [attributes umlaut]
  (filter #(draw-edge? % umlaut) attributes))

(defn- filter-methods-in-map
  "Filter methods of a declaration block that are inside any of the groups"
  [methods umlaut]
  (filter #(draw-edge? (% :return) umlaut) methods))

(defn- get-all-param-types [methods]
  (flatten (reduce (fn [acc el] (conj acc (el :params))) [] methods)))

(defn- filter-args-in-map
  "Filter method arguments of a declaration block that are inside any of the groups"
  [methods umlaut]
  (let [args (get-all-param-types methods)]
    (filter #(draw-edge? % umlaut) args)))

(defn- attr-types-from-node [node umlaut]
  "Returns a list of Strings of all non primitive types"
  (map #(get % :type-id) (filter-attr-in-map (get node :attributes) umlaut)))

(defn- method-types-from-node [node umlaut]
  "Returns a list of Strings of all non primitive types"
  (map #((% :return) :type-id) (filter-methods-in-map (node :methods) umlaut)))

(defn- method-args-from-node [node umlaut]
  "Returns a list of Strings of all non primitive types"
  (map #(% :type-id) (filter-args-in-map (node :methods) umlaut)))

(defn- edge-label [src dst]
  "Returns a dot string that represents an edge"
  (let [label (str src " -> " dst "\n")]
    (if (not (in? label (deref edges)))
      (do
        (swap! edges conj label)
        label)
      "")))

(defn- edge-inheritance-label [src dst]
  "Returns a dot string that represents an inheritance edge"
  (str "edge [arrowhead = \"empty\"]\n" (edge-label src dst) "\nedge [arrowhead = \"open\"]\n"))

(defn- edge-args-label [src dst]
  "Returns a dot string that represents an inheritance edge"
  (str (edge-label src dst) " [style=dotted]\n"))

(defn- build-edges-inheritance [node parents umlaut]
  "Builds a string with all inheritance edges (multiple inheritance case)"
  (string/join "\n" (map (fn [parent]
                          (edge-inheritance-label (node :id) (get parent :type-id)))
                      (filter-attr-in-map parents umlaut))))

(defn- build-edges-attributes [node umlaut]
  "Builds a string with all the regular edges between a type and its attributes"
  (string/join "\n" (map (fn [type] (edge-label (node :id) type)) (attr-types-from-node node umlaut))))

(defn- build-edges-methods [node umlaut]
  "Builds a string with all the regular edges between a type and its methods"
  (str
    (string/join "\n" (map (fn [type] (edge-label (node :id) type)) (method-types-from-node node umlaut)))
    (string/join "\n" (map (fn [type] (edge-args-label (node :id) type)) (method-args-from-node node umlaut)))))

(defn- contain-parents? [node]
  "Whether the type inherits from other types or not"
  (and (contains? node :parents) (> (count (get node :parents)) 0)))

(defn gen-edges
  [umlaut]
  "Generate a string with all the edges that should be drawn"
  (reduce (fn [acc [id node]]
            (let [block (second node)]
              (str acc (build-edges-attributes block umlaut)
                (build-edges-methods block umlaut)
                (when (contain-parents? block)
                  (build-edges-inheritance block (block :parents) umlaut)))))
   "" (seq (umlaut :nodes))))

(defn gen-subgraphs-string [umlaut]
  "Generate the dot language subgraph and its edges"
  (str (gen-subgraph umlaut) (gen-edges umlaut)))

(defn format-filename [filename]
  "Ensures that the output file is saved in the output folder"
  (str "output/" filename ".png"))

(defn save-diagram [filename dotstring]
  (println (str "Saving " filename))
  ; (println dotstring)
  (let [error (sh "dot" "-Tpng" "-o" filename :in dotstring)]
    (when (= (error :exit) 1)
      (throw (Exception. (with-out-str (pprint error)))))))

(defn required-nodes [required coll]
  "Returns a map of all the nodes inside of coll that are in the required vector"
  (zipmap (sort required) (map second (sort-by first (filter #(in? (first %) required) coll)))))

(defn- remove-extra-nodes [diagram required umlaut]
  "Rebuild umlaut structure with the nodes key containing only required nodes for the diagram"
  (assoc (umlaut-base (required-nodes required (seq (umlaut :nodes))) (umlaut :diagrams))
    :current ((umlaut :diagrams) diagram)))

(defn- gen-dotstring [subgraphs]
  "Concatenates a fixed header, the subgraph string generated, and a fixed footer"
  (str header subgraphs footer))

(defn- filter-not-primitives
  "Filter attributes of a declaration block that are not primitive"
  [attributes]
  (filter #(not-primitive? (% :type-id)) attributes))

(defn- filter-not-primitives-methods
  "Filter attributes of a declaration block that are not primitive"
  [methods]
  (let [non-primitive (filter #(not-primitive? (% :type-id)) (get-all-param-types methods))]
    (distinct (concat non-primitive (map #(% :return) methods)))))

(defn- non-primitive-related-nodes [node]
  "Returns a list of Strings of all non primitive types"
  (concat (map #(% :type-id) (filter-not-primitives (node :attributes)))
          (map #(% :type-id) (filter-not-primitives-methods (node :methods)))))

(defn- adjacent-nodes [key graph]
  (pprint (non-primitive-related-nodes (second (graph key))))
  (let [block (second (graph key))
        adjs (non-primitive-related-nodes block)]
    (if (contain-parents? block)
      (flatten (merge adjs (map #(% :type-id) (block :parents))))
      adjs)))

(defn- dfs
  "Traverses a map given a starting point"
  [current graph visited]
  (if (not (in? current visited))
    (let [new-visited (distinct (conj visited current))
          attrs (adjacent-nodes current graph)]
      (if (> (count attrs) 0)
        (for [att attrs]
          (dfs att graph new-visited))
        new-visited))
    visited))

(defn- get-nodes-recursively [start umlaut]
  "Flatten all the reachable nodes from a starting node"
  (flatten (dfs start (umlaut :nodes) ())))

(defn- create-group [group umlaut]
  (if (= (last group) "!")
    (reduce (fn [acc start]
              (distinct (concat acc (get-nodes-recursively start umlaut))))
      [] (drop-last group))
    group))

(defn gen
  "Generate all diagrams based on the umlaut structure"
  [umlaut]
  (->> umlaut
    (gen-subgraphs-string)
    (gen-dotstring)
    (save-diagram (format-filename "all")))
  (reduce
    (fn [acc dobject]
      (def ^:private edges (atom []))
      (let [name (first dobject) node (second dobject)
            curr (gen-dotstring
                  (reduce (fn [acc group]
                            (str acc
                              (gen-subgraphs-string (remove-extra-nodes name
                                                      (create-group group umlaut) umlaut))))
                          "" ((second node) :groups)))]
        (save-diagram (format-filename name) curr)
        (assoc acc (format-filename name) curr)))
    {} (seq (umlaut :diagrams))))

(defn gen-diagrams [path]
  (gen (core/main path)))

(gen-diagrams "test/philz/main.umlaut")
