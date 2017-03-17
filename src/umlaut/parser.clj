(ns umlaut.parser
  (:require [clojure.java.io :as io]
            [instaparse.core :as insta]
            [umlaut.utils :as utils]))
(use '[clojure.pprint :only [pprint]])

(def parser
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
  [id & args] [:enum {:id id
                      :values args}])

(defn- to-kind
  [id & args] {:type-id id
               :arity (normalize-arity args)})

(defn- to-attribute
  [id & args] (assoc (first args) :id id :relationship-type :attribute))

(defn- to-parent
  [type] {:type-id type :relationship-type :parent})

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
           [type {:id id
                  :attributes (filter-relationsip-type :attribute args)
                  :parents (filter-relationsip-type :parent args)}]))

(defn- to-diagram
  [id & args] [:diagram {:id id
                         :groups (vec args)}])

(defn- to-diagram-group
  [& args] (vec args))

(defn- id-list [nodelist]
  "Given a list of nodes, return a list of all node ids"
  (map #(get (second %) :id) nodelist))

(defn- transformer
  [args]
  (let [node-list (insta/transform {:enum to-enum
                                     :arity-value #(if (not= "n" %) (read-string %) %)
                                     :kind to-kind
                                     :type (abstract-to-type :type)
                                     :parent to-parent
                                     :interface (abstract-to-type :interface)
                                     :attribute to-attribute
                                     :diagram to-diagram
                                     :diagram-group to-diagram-group} args)]
      {:nodes (zipmap (id-list (filter utils/not-diagram? node-list)) (filter utils/not-diagram? node-list))
       :diagrams (zipmap (id-list (filter utils/diagram? node-list)) (filter utils/diagram? node-list))}))

(defn parse
  [content]
  (->> (parser content) transformer))

; (parse (slurp "test/sample/main.umlaut"))
