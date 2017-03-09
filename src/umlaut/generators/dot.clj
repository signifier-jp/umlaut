(ns umlaut.generators.dot
  (:require [clojure.java.io :as io]
            [umlaut.models :as model]
            [clojure.spec :as s]
            [clojure.spec.test :as stest]))


(def header (slurp (io/resource "templates/header.dot")))

(def footer (slurp (io/resource "templates/footer.dot")))


(defn- arity-label
  [[from to]]
  (if (= from to) (str "[" from "]") (str "[" from ".." to "]")))

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
    :interface (str "\"{<<interface>>\n"
                    (:id type-obj) "|" (attributes-list type-obj) "}\"")
    :enum (str "\"{<<enum>>\n"
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
                   "}"))
            )
          "" namespaces))

(defn- gen-edges-from-namespace
  [namespace]
  "")

(defn gen-edges
  [namespaces]
  (reduce (fn [a namespace]
            (str a (gen-edges-from-namespace namespace)))
          "" namespaces))

(defn gen-all
  [namespaces]
  (str header
       (gen-nodes namespaces)
       ;; (gen-edges namespaces)
       footer))


(def g (gen-all (umlaut.core/-main "test/sample")))

(println g)

(umlaut.core/-main "test/sample")
