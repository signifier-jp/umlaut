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

(defn- get-annotations [node]
  (filter #(= (% :space) "lang/graphql") (node :annotations)))

(defn- get-identifier-annotation [annotations]
  (or (first (filter #(= (% :key) "identifier") annotations)) []))

(defn- gen-identifier [node]
  (if
    (= (count (get-annotations node)) 0)
    "type"
    (get (get-identifier-annotation (get-annotations node)) :value)))

(defmulti gen-entry (fn [obj] (first obj)))

(defmethod gen-entry :type [[_ body]]
  (str (gen-identifier body) " " (:id body) " {\n"
       (gen-attributes (:attributes body))
       "}\n\n"))

(defmethod gen-entry :enum [[_ body]]
  (str "enum " (:id body) " {\n"
       (gen-enum-values (:values body))
       "}\n\n"))

(defmethod gen-entry :default [[_ body]]
  "")

(defn write-file [filename content]
  (with-open [w (clojure.java.io/writer (str "output/" filename))]
    (.write w content)))

(defn gen
  [namespaces]
  (reduce (fn [acc [key node]]
            (str acc (gen-entry node)))
          "" (seq (namespaces :nodes))))

(write-file "main.graphql" (gen (umlaut.core/-main "test/graphql")))
(pprint (gen (umlaut.core/-main "test/graphql")))
