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
  [id & args]
  [:enum {:id id :values args}])

(defn- to-kind
  [id & args] {:type-id id :arity (normalize-arity args)})

(defn- to-attribute
  [id & args]
  (assoc (first args)
    :id id
    :relationship-type :attribute
    :required (= (first (last args)) :required)))

(defn- to-method-params [params]
  (vec params))

(defn- to-return [node]
  (assoc {} :type-id (second node) :required (= (first (last node)) :required)))

(defn- to-method
  [id & args]
  (assoc {}
    :id id
    :return (to-return (last args))
    :params (to-method-params (drop-last args))
    :relationship-type :method))

(defn- to-parent
  [type] {:type-id type :relationship-type :parent})

(defn- filter-relationship-type
  [relationship-id coll]
  (vec (map
         #(dissoc % :relationship-type)
         (filter
           #(= relationship-id (:relationship-type %))
           coll))))

(defn- filter-annotations
  [coll]
  (vec (map second (filter #(= (first %) :annotation) coll))))

(defn- abstract-to-type
  [type] (fn
            [id & args]
            (let [all (conj args id)
                  id (first (filter string? all))
                  args (filter #(not (string? %)) args)]
              [type {:id id
                      :attributes (filter-relationship-type :attribute all)
                      :methods (filter-relationship-type :method all)
                      :parents (filter-relationship-type :parent all)
                      :annotations (filter-annotations all)}])))

(defn- to-diagram
  [id & args] [:diagram {:id id
                         :groups (vec args)}])

(defn- to-diagram-group
  [& args] (vec args))

(defn- to-annotation
  [space-1 space-2 attribute value]
  [:annotation {:space (str space-1 "/" space-2)
                :key attribute
                :value value}])

(defn- id-list [nodelist]
  "Given a list of nodes, return a list of all node ids"
  (map #(get (second %) :id) nodelist))

(defn- transformer
  [args]
  (let [node-list (insta/transform {:enum to-enum
                                    :annotation to-annotation
                                    :arity-value #(if (not= "n" %) (read-string %) %)
                                    :kind to-kind
                                    :type (abstract-to-type :type)
                                    :parent to-parent
                                    :interface (abstract-to-type :interface)
                                    :attribute to-attribute
                                    :method to-method
                                    :diagram to-diagram
                                    :diagram-group to-diagram-group} args)]
      {:nodes (zipmap (id-list (filter utils/type-interface-or-enum? node-list)) (filter utils/type-interface-or-enum? node-list))
       :diagrams (zipmap (id-list (filter utils/diagram? node-list)) (filter utils/diagram? node-list))}))

(defn parse
  [content]
  (->> (parser content) transformer))

(pprint (parse (slurp "test/graphql/main.umlaut")))

