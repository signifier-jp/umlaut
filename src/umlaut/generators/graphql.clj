(ns umlaut.generators.graphql
  (:require [clojure.java.io :as io]
            [umlaut.models :as model]
            [clojure.spec :as s]
            [clojure.spec.test :as stest]))
(use '[clojure.pprint :only [pprint]])

(defn- gen-attribute
  [attribute]
  (str "  " (:id attribute) ": " (:type-id attribute) "\n"))

(defn- gen-attributes
  [attributes]
  (reduce (fn [a attribute] (str a (gen-attribute attribute)))
          "" attributes))

(defn- gen-enum-values
  [values]
  (reduce (fn [a value] (str a (str "  " value "\n")))
          "" values))

(defmulti gen-entry (fn [obj] (first obj)))

(defmethod gen-entry :type [[_ body]]
  (str "type " (:id body) " {\n"
       (gen-attributes (:attributes body))
       "}\n\n"))

(defmethod gen-entry :enum [[_ body]]
  (pprint body)
  (str "enum " (:id body) " {\n"
       (gen-enum-values (:values body))
       "}\n\n"))

(defmethod gen-entry :default [[_ body]]
  "")

(defn gen
  [namespaces]
  (reduce (fn [acc [key node]]
            (str acc (gen-entry node)))
          "" (seq (namespaces :nodes))))

(pprint (gen (umlaut.core/-main "test/graphql")))
