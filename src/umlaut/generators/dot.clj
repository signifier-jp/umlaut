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

(defn generate
  [namespaces]
  (println (attributes-label (:type (first (second (first namespaces))))))
  (spit "testeeee.dot" (str header footer)))




(umlaut.generators.dot/generate (umlaut.core/-main "test/sample"))