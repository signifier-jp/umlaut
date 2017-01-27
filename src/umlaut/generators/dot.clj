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

(defn- attributes-list
  [{:keys [attributes]}]
  (reduce #(str %1 "+ " (:id %2) " : " (:type-id %2)
                (arity-label (:arity %2)) "\\l")
          "" attributes))

(defn- attributes-label
  [type-obj]
  (str "\"{" (:id type-obj) "|" (attributes-list type-obj) "}\""))


(defmulti node-str (fn [[type-id _]] type-id))

(defmethod node-str :type
  [[_ type-obj]]
  (str (:id type-obj) " [ label = " (attributes-label type-obj) " ]"))

(defmethod node-str :interface
  [[_ type-obj]] (node-str [:type type-obj]))

(defmethod node-str :enum
  [[_ type-obj]] "")

(defmethod node-str :diagram
  [[_ type-obj]] "")

(defn- namespace-id [[namespace-path _]]
  (second (re-matches #".*\/(.+)\.umlaut$" namespace-path)))


(defn- gen-nodes-from-namespace
  [namespace]
  (let [namespace-coll (second namespace)]
    (reduce (fn [a x] (str a (node-str x) "\n")) "" namespace-coll)))

(defn gen-nodes
  [namespaces]
  (reduce (fn [a namespace]
            (str "subgraph "
                 (namespace-id namespace) "{ label = \"" (namespace-id namespace) "\"\n"
                 (str a (gen-nodes-from-namespace namespace))
                 "}"))
          "" namespaces))



(defn gen-all
  [namespaces]
  (str header (gen-nodes namespaces) footer))


(def g (gen-all (umlaut.core/-main "test/sample")))
(println g)