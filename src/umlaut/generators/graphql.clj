(ns umlaut.generators.graphql
  (:require [clojure.java.io :as io]
            [umlaut.models :as model]
            [clojure.spec :as s]
            [clojure.spec.test :as stest]))

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
  (str "enum " (:id body) " {\n"
       (gen-enum-values (:values body))
       "}\n\n"))

(defmethod gen-entry :default [[_ body]]
  "")

(defn- gen-namespace
  [namespace]
  (reduce (fn [a obj]
            (str a (gen-entry obj)))
          "" namespace))

(defn gen
  [namespaces]
  (reduce (fn [a namespace]
            (str a (gen-namespace (second namespace))))
          "" namespaces))

(gen (umlaut.core/-main "test/sample"))
