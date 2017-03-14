(ns umlaut.generators.dot
  (:require [clojure.java.io :as io]
            [umlaut.models :as model]
            [umlaut.utils :as util]
            [clojure.spec :as s]
            [clojure.string :as string]
            [clojure.spec.test :as stest]))
(use '[clojure.java.shell :only [sh]])

(def header (slurp (io/resource "templates/header.dot")))

(def footer (slurp (io/resource "templates/footer.dot")))

(defn- arity-label
  [[from to]]
  (if (= from to) (str "") (str "[" from ".." to "]")))

(defn- namespace-id [[namespace-path _]]
  (second (re-matches #".*\/(.+)\.umlaut$" namespace-path)))

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
  [[kind type-obj]]
  (str (:id type-obj)
       " [ label = "
       (node-label kind type-obj)
       " ]"))

(defn- gen-nodes-from-namespace
  [namespace]
  (->> (second namespace)
       (reduce (fn [a x] (str a (node-str x) "\n")) "")))

(defn gen-nodes
  [namespaces]
  (reduce (fn [a namespace]
    (let [ns-id (namespace-id namespace)]
      (str "subgraph "
           ns-id
           " { label = \""
           ns-id
           "\"\n"
           (str a (gen-nodes-from-namespace namespace))
           "}")))
  "" namespaces))

(defn- node-id [node]
  (get node :id))

(defn- non-primitive
  "Filter attributes of a declaration block that are not primitive"
  [attributes]
  (filter #(util/not-primitive? (get % :type-id)) attributes))

(defn- attr-types-from-declaration [declaration]
  "Returns a list of Strings of all non primitive types"
  (map #(get % :type-id) (non-primitive (get declaration :attributes))))

(defn- edge-label [src dst]
  "Returns a dot string that represents an edge"
  (str src " -> " dst "\n"))

(defn- edge-inheritance-label [src dst]
  "Returns a dot string that represents an inheritance edge"
  (str "edge [arrowhead = \"empty\"]\n" (edge-label src dst) "\nedge [arrowhead = \"open\"]\n"))

(defn- build-edges-inheritance [block parents]
  "Builds a string with all inheritance edges (multiple inheritance case)"
  (string/join "\n" (map (fn [parent] (edge-inheritance-label (node-id block) (get parent :type-id))) parents)))

(defn- build-edges-attributes [block]
  "Builds a string with all the regular edges between a type and its attributes"
  (string/join "\n" (map (fn [type] (edge-label (node-id block) type)) (attr-types-from-declaration block))))

(defn- contain-parents? [block]
  "Whether the type inherits from other types or not"
  (and (contains? block :parents) (> (count (get block :parents)) 0)))

(defn- gen-edges-from-namespace
  "Generate a string with all the edges that should be drawn"
  [[filename content]]
  (reduce (fn [acc declaration]
    (str acc (let [block (second declaration)]
      (str (build-edges-attributes block)
        (when (contain-parents? block) (build-edges-inheritance block (get block :parents)))))))
   "" content))

(defn gen-edges
  [namespaces]
  (reduce (fn [acc namespace]
      (str acc (gen-edges-from-namespace namespace)))
    "" namespaces))

(defn gen-dotstring [namespace]
  (str header
    (gen-nodes namespace)
    (gen-edges namespace)
    footer))

(defn format-filename [filename]
  (string/replace (str "output/" (last (string/split filename #"\/"))) #"umlaut" "png"))

(defn save-diagram [filename dotstring]
  (println (str "Saving " filename))
  (println dotstring)
  (sh "dot" "-Tpng" "-o" filename :in dotstring))

(defn gen-all
  [namespaces]
  (save-diagram (format-filename "a.png") (gen-dotstring namespaces)))


(gen-all (umlaut.core/-main "test/sample"))