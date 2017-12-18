(ns umlaut.utils
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as string]
            [clojure.pprint :refer [pprint]]))

(def primitive-types ["String" "Float" "Integer" "Boolean" "DateTime", "ID"])

(defn primitive? "Whether type is primitive or not" [type]
  (pos? (count (filter (fn* [p1__19405#] (= (string/lower-case p1__19405#) (string/lower-case type))) primitive-types))))

(defn not-primitive? "Whether type is not a primitive" [type]
  (not (primitive? type)))

(defn diagram?
  "Whether an AST node is a diagram node or not"
  [node]
  (= (first node) :diagram))

(defn type?
  "Whether an AST node is a type node or not"
  [node]
  (= (first node) :type))

(defn interface?
  "Whether an AST node is an interface node or not"
  [node]
  (= (first node) :interface))

(defn enum?
  "Whether an AST node is an enum node or not"
  [node]
  (= (first node) :enum))

(defn type-interface-or-enum?
  "Whether an AST node is an interface node or not"
  [node]
  (or (type? node) (interface? node) (enum? node)))

(defn seek [func coll]
  "Returns the first occurrence when func is true in coll"
  (first (filter func coll)))

(defn umlaut-base [nodes diagrams]
  "Base map structure of the ast data structure"
  {:nodes nodes :diagrams diagrams})

(defn in?
  "Whether collection has the element inside"
  [element collection]
  (some #(= element %) collection))

(defn annotations-by-space [space annotations]
  "Filter an array of annotation by space"
  (filter #(= (% :space) space) annotations))

(defn annotations-by-key [key annotations]
  "Filter an array of annotation by key"
  (filter #(= (% :key) key) annotations))

(defn annotations-by-space-key [space key annotations]
  "Filter an array of annotation by key and value"
  (filter #(and (= space (% :space)) (= key (% :key))) annotations))

(defn annotations-by-key-value [key value annotations]
  "Filter an array of annotation by key and value"
  (filter #(and (= value (% :value)) (= key (% :key))) annotations))

(defn annotations-by-space-key-value [space key value annotations]
  "Filter an array of annotation by space, key, and value"
  (filter #(and (= space (% :space)) (= value (% :value)) (= key (% :key))) annotations))

(defn annotation-comparer [space key value]
  "To be used as filter function of (seq (umlaut :nodes))"
  (fn [node]
    (let [annotations (annotations-by-space space ((last (last node)) :annotations))]
      (pos? (->> annotations (filter (fn* [p1__94482#] (and (= value (p1__94482# :value)) (= "identifier" (p1__94482# :key))))) (count))))))

(defn save-map-to-file [filepath content]
  "Receives a file name and a map, prints the map into a string and saves the string in filepath"
  (println (str "Saved " filepath))
  (spit filepath (with-out-str (pprint content))))

(defn save-string-to-file [filepath content]
  "Receives a file name and a string, saves the string in filepath"
  (println "Saved " filepath)
  (spit filepath content))

(defn save-dotstring-to-image [filepath content]
  "Receives a file name and a dotstring, runs dot and saves the resulting image"
  (let [error (sh "dot" "-Tpng" "-o" filepath :in content)]
    (if (= (error :exit) 1)
      (do
        (println "You need graphiz to generate diagrams, are you sure you have it installed?")
        (throw (Exception. (with-out-str (pprint error)))))
      (println (str "Saved " filepath)))))

(defn- get-parent-node [parent umlaut]
  (second ((umlaut :nodes) (parent :type-id))))

(defn- resolve-field-inheritance [node umlaut]
  (reduce (fn [acc parent]
            (concat acc ((get-parent-node parent umlaut) :fields)))
          (node :fields) (node :parents)))

(defn- resolve-node-inheritance [node umlaut]
  (assoc node :fields (resolve-field-inheritance node umlaut)))

(defn- resolve-nodes-inheritance [umlaut]
  (reduce (fn [acc [id [kind obj]]]
            (assoc acc id [kind (resolve-node-inheritance obj umlaut)]))
          {} (seq (umlaut :nodes))))

(defn resolve-inheritance [umlaut]
  (assoc umlaut :nodes (resolve-nodes-inheritance umlaut)))

(defn union? [node]
  (pos? (count (annotations-by-key-value "identifier" "union" (node :annotations)))))
