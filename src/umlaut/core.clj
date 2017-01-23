(ns umlaut.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [instaparse.core :as insta])
  (:gen-class))

(def parse
  (insta/parser
    (io/resource "umlaut.bnf")))

(defn- to-enum
  [id & args] {:enum {:id id}
               :values args})

(defn- to-optional
  [& args] {:optional true})

(defn- to-single
  [id & args] {:type id
               :arity [1]})

(defn- to-collection
  [id & args] {:type id
               :arity (vec args)})

(def transformers
  {:enum to-enum
   :from read-string
   :to #(if (not=  "n" %) (read-string %) %)
   :single to-single
   :collection to-collection
   :optional to-optional})


(defn -main
  [path & args]
  (->> path
       (io/file)
       (file-seq)
       (map #(.getPath %))
       (filter #(str/ends-with? % ".umlaut"))
       (map #(slurp %))
       (reduce #(conj %1 (parse %2)) '())))

;(-main "test/sample")

(parse (slurp "test/sample/sample.umlaut"))

(insta/transform transformers (parse (slurp "test/sample/sample.umlaut")))