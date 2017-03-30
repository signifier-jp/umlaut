(ns umlaut.generators.dot
  (:require [clojure.java.io :as io]
            [umlaut.models :as model]
            [umlaut.utils :refer :all]
            [clojure.spec :as s]
            [clojure.string :as string]
            [clojure.spec.test :as stest]))
(use '[clojure.java.shell :only [sh]])
(use '[clojure.pprint :only [pprint]])

(def header (slurp (io/resource "templates/header.dot")))

(def footer (slurp (io/resource "templates/footer.dot")))

(def edges [])

(defn- arity-label
  "Builds the arity representation in the diagram"
  [[from to]]
  (if (= from to) (str "") (str "[" from ".." to "]")))

(defn- attributes-list
  [{:keys [attributes]}]
  (reduce #(str %1 "+ " (:id %2) " : " (:type-id %2)
                (arity-label (:arity %2)) "\\l")
          "" attributes))

(defn- values-list
  [{:keys [values]}]
  (reduce #(str %1 "+ " %2 "\\l")
          "" values))

(defn- node-label
  "Builds the string regarding object type"
  [kind type-obj]
  (case kind
    :type (str "\"{"
               (:id type-obj) "|" (attributes-list type-obj) "}\"")
    :interface (str "\"{\\<\\<interface\\>\\>"
                    (:id type-obj) "|" (attributes-list type-obj) "}\"")
    :enum (str "\"{\\<\\<enum\\>\\>"
               (:id type-obj) "|" (values-list type-obj) "}\"")
    ""))

(defn- node-str
  "Receives an AST node and generates the proper dotstring"
  [[kind type-obj]]
  (str "  " (:id type-obj)
    " [label = "
    (node-label kind type-obj)
    "]"))

(defn- gen-subgraph-content
  [umlaut]
  (reduce (fn [acc [id node]]
            (str acc (node-str node) "\n")) "" (seq (umlaut :nodes))))

(defn gen-subgraph
  [umlaut]
  (let [ns-id (gensym)]
   (str "subgraph "
        ns-id
        " {\n  label = \""
        ns-id
        "\"\n"
        (gen-subgraph-content umlaut)
        "}\n")))

(defn- getGroupSet
  "Receives a umlaut structure and returns a set with all the boxes that will be drawn"
  [umlaut]
  (set (flatten ((second (umlaut :current)) :groups))))

(defn- draw-edge? [node umlaut]
  (when (= ((second (umlaut :current)) :id) "all2"))
  (or
    (contains? (getGroupSet umlaut) (node :type-id))
    (in? (node :type-id) (keys (umlaut :nodes)))))

(defn- filter-attr-in-map
  "Filter attributes of a declaration block that are inside any of the groups"
  [attributes umlaut]
  (filter #(draw-edge? % umlaut) attributes))

(defn- attr-types-from-node [node umlaut]
  "Returns a list of Strings of all non primitive types"
  (map #(get % :type-id) (filter-attr-in-map (get node :attributes) umlaut)))

(defn- edge-label [src dst]
  "Returns a dot string that represents an edge"
  (let [label (str src " -> " dst "\n")]
    (if (not (in? label edges))
      (do
        (def edges(conj edges label))
        label)
      "")))

(defn- edge-inheritance-label [src dst]
  "Returns a dot string that represents an inheritance edge"
  (str "edge [arrowhead = \"empty\"]\n" (edge-label src dst) "\nedge [arrowhead = \"open\"]\n"))

(defn- build-edges-inheritance [node parents umlaut]
  "Builds a string with all inheritance edges (multiple inheritance case)"
  (string/join "\n" (map (fn [parent]
                          (edge-inheritance-label (node :id) (get parent :type-id)))
                      (filter-attr-in-map parents umlaut))))

(defn- build-edges-attributes [node umlaut]
  "Builds a string with all the regular edges between a type and its attributes"
  (string/join "\n" (map (fn [type] (edge-label (node :id) type)) (attr-types-from-node node umlaut))))

(defn- contain-parents? [node]
  "Whether the type inherits from other types or not"
  (and (contains? node :parents) (> (count (get node :parents)) 0)))

(defn gen-edges
  [umlaut]
  "Generate a string with all the edges that should be drawn"
  (reduce (fn [acc [id node]]
            (let [block (second node)]
              (str acc (build-edges-attributes block umlaut)
                (when (contain-parents? block) (build-edges-inheritance block (block :parents) umlaut)))))
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
  (sh "dot" "-Tpng" "-o" filename :in dotstring))

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

(defn- attr-types-from-node-not-primitive [node]
  "Returns a list of Strings of all non primitive types"
  (map #(% :type-id) (filter-not-primitives (node :attributes))))

(defn- adjacent-nodes [key graph]
  (let [block (second (graph key))
        adjs (attr-types-from-node-not-primitive block)]
    (if (contain-parents? block)
      (flatten (merge adjs (map #(% :type-id) (block :parents))))
      adjs)))

(defn dfs
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

(defn get-nodes-recursively [start umlaut]
  (flatten (dfs start (umlaut :nodes) ())))

(defn create-group [group umlaut]
  (if (= (last group) "!")
    (reduce (fn [acc start]
              (distinct (concat acc (get-nodes-recursively start umlaut))))
      [] (drop-last group))
    group))

(defn gen-diagrams
  "Generate all diagrams based on the umlaut code"
  [umlaut]
  (for [dobject (seq (umlaut :diagrams))]
    (do
      (def edges [])
      (let [name (first dobject) node (second dobject)]
        (save-diagram (format-filename name)
          (gen-dotstring
            (reduce (fn [acc group]
                      (str acc
                        (gen-subgraphs-string (remove-extra-nodes name
                                                (create-group group umlaut) umlaut))))
              "" ((second node) :groups))))))))

(gen-diagrams (umlaut.core/-main "test/tickets"))
