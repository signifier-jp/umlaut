(ns umlaut.parser
  (:require [clojure.java.io :as io]
            [instaparse.core :as insta]
            [umlaut.utils :as utils]))
(use '[clojure.pprint :only [pprint]])
(use '[clojure.string :only (split)])

(def parser
  (insta/parser
    (io/resource "umlaut.bnf")))

(defn- normalize-arity
  "Formats :arity according to the number of args"
  [args]
  (case (count args)
    0 [1 1]
    1 (let [lone (first args)]
        (if (= "n" lone) [1 lone] [lone lone]))
    2 (vec args)))

(defn- to-enum
  "Transforms AST :enum to enum map"
  [id & args]
  [:enum {:id id :values args}])

(defn- to-kind
  "Transforms AST :kind to kind map"
  [id & args] {:type-id id :arity (normalize-arity args)})

(defn- required? [args]
  "Retruns a boolean whether the attribute/param is required or not"
  (not (= (first (last args)) :optional)))

(defn- to-attribute
  "Transforms AST :attribute to attribute map"
  [id & args]
  (assoc (first args)
    :id id
    :relationship-type :attribute
    :required (required? args)))

(defn- to-method-params [params]
  (vec params))

(defn- to-return [node]
  "Transforms AST :return into return map"
  (let [return (second node)]
    (merge return {:required (required? node)})))

(defn- to-method
  "Transforms AST :method into method map"
  [id & args]
  (assoc {}
    :id id
    :return (to-return (last args))
    :params (to-method-params (drop-last args))
    :relationship-type :method))

(defn- to-parent
  "Creates a parent map"
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
  "Transforms AST :diagram to diagram map"
  [id & args] [:diagram {:id id
                         :groups (vec args)}])

(defn- to-diagram-group
  [& args] (vec args))

(defn- to-annotation
  "Transforms AST :annotation to annotation map"
  [space-1 space-2 attribute values]
  (let [parsed (split values #" ")]
    [:annotation {:space (str space-1 "/" space-2)
                  :key attribute
                  :value (if (= (count parsed) 1) (first parsed) parsed)}]))

(defn- id-list [nodelist]
  "Given a list of nodes, return a list of all node ids"
  (map #(get (second %) :id) nodelist))

(defn- transformer
  [ast]
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
                                    :diagram-group to-diagram-group} ast)]
      {:nodes (zipmap (id-list (filter utils/type-interface-or-enum? node-list)) (filter utils/type-interface-or-enum? node-list))
       :diagrams (zipmap (id-list (filter utils/diagram? node-list)) (filter utils/diagram? node-list))}))

(defn parse
  [content]
  (let [parsed (parser content)]
    (pprint (insta/get-failure parsed))
    (transformer parsed)))
