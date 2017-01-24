(ns umlaut.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [instaparse.core :as insta])
  (:gen-class))

(def parse
  (insta/parser
    (io/resource "umlaut.bnf")))

(defn- normalize-arity
  [args]
  (case (count args)
    0 [1 1]
    1 (let [lone (nth args 0)]
        (if (= "n" lone) [1 lone] [lone lone]))
    2 (vec args)))

(defn- to-enum
  [id & args] {:enum {:id id
               :values args}})

(defn- to-kind
  [id & args] {:type id
               :arity (normalize-arity args)})

(defn- to-attribute
  [id & args] (assoc (first args) :id id :relationship-type :attribute))

(defn- to-parent
  [type] {:type type :relationship-type :parent})

(defn- filter-relationsip-type
  [relationship-id coll]
  (vec (map
    #(dissoc % :relationship-type)
    (filter
      #(= relationship-id (:relationship-type %))
      coll))))

(defn- abstract-to-type
  [type] (fn
           [id & args]
           {type {:id id
                  :attributes (filter-relationsip-type :attribute args)
                  :parents (filter-relationsip-type :parent args)}}))

(defn- to-diagram
  [id & args] {:diagram {:id id
                         :groups (vec args)}})

(defn- to-diagram-group
  [& args] (vec args))

(defn- transformer
  [args] (insta/transform {:enum to-enum
                             :arity-value #(if (not= "n" %) (read-string %) %)
                             :kind to-kind
                             :type (abstract-to-type :type)
                             :parent to-parent
                             :interface (abstract-to-type :interface)
                             :attribute to-attribute
                             :diagram to-diagram
                             :diagram-group to-diagram-group} args))

(defn -main
  [path & args]
  (->> path
       (io/file)
       (file-seq)
       (map #(.getPath %))
       (filter #(str/ends-with? % ".umlaut"))
       (map (fn [x] {:file-name x :content (slurp x)}))
       (reduce
         #(assoc %1 (:file-name %2)
                    (->> (parse (:content %2)) transformer))
         '{})))

(-main "test/sample")

;(parse (slurp "test/sample/sample.umlaut"))