(ns umlaut.generators.dot
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [umlaut.core :as core]
            [umlaut.utils :refer [in?
                                  union?
                                  annotations-by-space-key
                                  umlaut-base
                                  not-primitive?]]))

(def header (slurp (io/resource "templates/header.dot")))

(def footer (slurp (io/resource "templates/footer.dot")))

;; Store all edges added in a subgraph, so we don't have redundant edges
(def ^:private edges (atom []))

(defn- arity-label
  "Builds the arity representation string in the diagram"
  [[from to]]
  (if (= from to) (if (= from 1) "" (str "[" from "]")) (str "[" from ".." to "]")))

(defn- required-label [type-obj]
  "Checks required attribute and returns ? or not"
  (if (type-obj :required)
    ""
    "?"))

(defn- type-label [type]
  "Receives a ::attribute object and builds the string for its type"
  (str (type :type-id) (arity-label (type :arity)) (required-label type)))

(defn- attribute-label [method]
  "Receives a ::method object and build its complete label"
  (str (:id method) ": " (type-label (method :return)) "\\l"))

(defn- method-args-label [method]
  "Iterates over all the method arguments and builds the string"
  (->> (method :params)
       (map #(str (%1 :id) ": " (type-label %1)))
       (string/join ", ")))

(defn- method-label [method]
  "Receives a ::method object and build its complete label"
  (str (method :id) "(" (method-args-label method) "): " (type-label (method :return)) "\\l"))

(defn- fields-list [fields]
  (reduce (fn [acc method]
            (str acc (if (method :params?)
                       (method-label method)
                       (attribute-label method))))
          "" fields))

(defn- values-list
  [{:keys [values]}]
  (reduce #(str %1 %2 "\\l") "" values))

(defn- enum-or-union [type-obj]
  (if (union? type-obj)
    "union"
    "enum"))

(defn- node-label
  "Builds the string regarding object type"
  [kind type-obj]
  (case kind
    :type (str (:id type-obj) "|" (fields-list (type-obj :fields)))
    :interface (str "\\<\\<interface\\>\\>\\n" (:id type-obj) "|" (fields-list (type-obj :fields)))
    :enum (str "\\<\\< " (enum-or-union type-obj) "\\>\\>\\n" (:id type-obj) "|" (values-list type-obj))
    ""))

(defn- node-color [type-obj]
  (let [color (first (annotations-by-space-key "lang/dot" "color" (type-obj :annotations)))]
    (if color
      (str " fillcolor = " (color :value) ", style=filled")
      "")))

(defn- node-str
  "Receives an AST node and generates the dotstring of the node"
  [[kind type-obj]]
  (str "  " (:id type-obj)
       " [label = \"{"
       (node-label kind type-obj)
       "}\"" (node-color type-obj) "]"))

(defn- gen-subgraph-content
  [umlaut]
  (reduce (fn [acc [id node]]
            (str acc (node-str node) "\n"))
          "" (seq (umlaut :nodes))))

(defn- subgraph-id [umlaut]
  (->> (seq (umlaut :nodes))
       (first)
       (first)))

(defn- gen-subgraph
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

(defn- method-types-from-node [node umlaut]
  "Returns a list of type objects of all non primitive types"
  (map #(% :return) (filter-methods-in-map (node :fields) umlaut)))

(defn- method-args-from-node [node umlaut]
  "Returns a list of type objects of all non primitive arguments from a method"
  (filter-args-in-map (node :fields) umlaut))

(defn- build-edge-label [type-obj]
  (if (type-obj :arity)
    (let [[from to] (type-obj :arity)]
      (if (= from to) from (str from ".." to)))
    ""))

(defn- edge-label [src dst]
  "Returns a dot string that represents an edge"
  (let [label (str src " -> " (dst :type-id) " [label=\"" (build-edge-label dst) "\"]" "\n")]
    (if-not (in? label (deref edges)) (do (swap! edges conj label) label) "")))

(defn- edge-inheritance-label [src dst]
  "Returns a dot string that represents an inheritance edge"
  (str "edge [arrowhead = \"empty\"]\n" (edge-label src dst) "\nedge [arrowhead = \"open\"]\n"))

(defn- edge-params-label [src dst]
  "Returns a dot string that represents an argument edge"
  (str (edge-label src dst) " [style=dotted]\n"))

(defn- edge-union-label [src dst]
  "Returns a dot string that represents an union edge"
  (str (edge-label src {:type-id dst :arity [1 1]}) " [style=dashed]\n"))

(defn- build-edges-fields [node umlaut]
  "Builds a string with all the regular edges between a type and its methods"
  (str
   (string/join "\n" (map (fn [type-obj] (edge-label (node :id) type-obj)) (method-types-from-node node umlaut)))
   (string/join "\n" (map (fn [type-obj] (edge-params-label (node :id) type-obj)) (method-args-from-node node umlaut)))))

(defn- build-edges-inheritance [node parents umlaut]
  "Builds a string with all inheritance edges (multiple inheritance case)"
  (string/join "\n" (map (fn [parent]
                           (edge-inheritance-label (node :id) parent))
                         (filter-attr-in-map parents umlaut))))

(defn- build-edges-union [node]
  (string/join "\n" (map (fn [value] (edge-union-label (node :id) value)) (node :values))))

(defn- contain-parents? [node]
  "Whether the type inherits from other types or not"
  (and (contains? node :parents) (pos? (count (get node :parents)))))

(defn gen-edges
  [umlaut]
  "Generate a string with all the edges that should be drawn"
  (reduce (fn [acc [_ node]]
            (let [block (second node)]
              (str acc (build-edges-fields block umlaut)
                   (when (union? block)
                     (build-edges-union block))
                   (when (contain-parents? block)
                     (build-edges-inheritance block (block :parents) umlaut)))))
          "" (seq (umlaut :nodes))))

(defn gen-subgraphs-string [umlaut]
  "Generate the dot language subgraph and its edges"
  (str (gen-subgraph umlaut) (gen-edges umlaut)))

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

(defn- filter-not-primitives-methods
  "Filter attributes of a declaration block that are not primitive"
  [methods]
  (let [all-types (concat
                   (map #(% :type-id) (get-all-param-types methods))
                   (map #(% :type-id) (map #(% :return) methods)))]
    (distinct (filter not-primitive? all-types))))

(defn- non-primitive-related-nodes [node]
  "Returns a list of Strings of all non primitive types"
  (if node
    (if (union? node)
      (distinct (filter not-primitive? (node :values)))
      (filter-not-primitives-methods (node :fields)))
    ()))

(defn- adjacent-nodes [key graph]
  (let [block (second (graph key))
        adjs (non-primitive-related-nodes block)]
    (if (contain-parents? block)
      (flatten (merge adjs (map #(% :type-id) (block :parents))))
      adjs)))

(defn- dfs
  "Traverses a map given a starting point"
  [current graph visited]
  (if-not (in? current visited)
    (let [new-visited (distinct (conj visited current))
          attrs (adjacent-nodes current graph)]
      (if (pos? (count attrs))
        (for [att attrs] (dfs att graph new-visited)) new-visited)) visited))

(defn- get-nodes-recursively [start umlaut]
  "Flatten all the reachable nodes from a starting node"
  (flatten (dfs start (umlaut :nodes) ())))

(defn- create-group [group umlaut]
  (if (= (last group) "!")
    (reduce (fn [acc start]
              (distinct (concat acc (get-nodes-recursively start umlaut))))
            [] (drop-last group))
    group))

(defn gen-all [umlaut]
  (def ^:private edges (atom []))
  (let [dotstring (->> umlaut
                       (gen-subgraphs-string)
                       (gen-dotstring))]
    dotstring))

(defn gen-by-group [umlaut]
  (reduce
   (fn [acc [diagram-name node]]
     (def ^:private edges (atom []))
     (let
      [curr
       (->> ((second node) :groups)
            (reduce
             (fn [acc group]
               (str acc
                    (gen-subgraphs-string
                     (remove-extra-nodes diagram-name (create-group group umlaut) umlaut))))
             "")
            (gen-dotstring))]
       (assoc acc diagram-name curr)))
   {} (seq (umlaut :diagrams))))

(defn gen [files]
  "Saves the diagrams in the /output folder"
  (let [umlaut (core/main files)]
    (gen-by-group umlaut)
    (gen-all umlaut)))

; (save-string-to-file "output/a.dot" (gen ["test/fixtures/person/person.umlaut" "test/fixtures/person/profession.umlaut"]))
; (save-dotstring-to-image "output/all.png" (gen ["test/fixtures/person/person.umlaut" "test/fixtures/person/profession.umlaut"]))
; (save-dotstring-to-image "output/philz.png" (gen ["test/philz/main.umlaut"]))
; (save-dotstring-to-image "output/products.png" (get (gen-by-group (core/main ["test/philz/main.umlaut"])) "Products"))
; (save-dotstring-to-image "output/employee.png" (get (gen-by-group (core/main ["test/fixtures/person/person.umlaut" "test/fixtures/person/profession.umlaut"])) "fixture"))
