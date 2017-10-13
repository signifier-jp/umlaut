(ns umlaut.parser
  (:require [clojure.java.io :as io]
            [clojure.string :refer [split]]
            [instaparse.core :as insta]
            [umlaut.utils :refer [seek
                                  type-interface-or-enum?
                                  diagram?]]))

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
  (let [all (conj args id)
        realId (first (filter string? all))
        values (filter #(and (string? %) (not= realId %)) all)
        annotations (remove string? all)]
    [:enum {:id realId
            :values values
            :annotations (vec (map second annotations))}]))

(defn- to-kind
  "Transforms AST :kind to kind map"
  [id & args] {:type-id id :arity (normalize-arity args)})

(defn- required? [args]
  "Retruns a boolean whether the attribute/param is required or not"
  (not= (first (last args)) :optional))

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

(defn- process-annotations [args]
  (let [field (seek #(= (first %) :field-annotations) args)]
    (reduce
     (fn [acc el]
       (when (= (first el) :annotation)
         (let [obj (second el)]
           (case (obj :space)
             :documentation (assoc acc :documentation (obj :value))
             :deprecation (assoc acc :deprecation (obj :value))
             (merge acc {:others (conj (or (acc :others) []) (second el))})))))
     {} (rest field))))

(defn- to-method
  "Transforms AST :method into method map"
  [id & args]
  (assoc {}
         :id id
         :return (to-return (first (filter #(= (first %) :return-kind) args)))
         :params (to-method-params (filter map? args))
         :params? (pos? (count (to-method-params (filter map? args))))
         :field-annotations (process-annotations args)
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
                 args (remove string? args)]
             [type {:id id
                    :fields (filter-relationship-type :method all)
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

(defn- to-documentation-annotation [kind value]
  (let [annon (case kind
                "doc" {:space :documentation :key "" :value value}
                "deprecation" {:space :deprecation :key "" :value value})]
    [:annotation annon]))

(defn- id-list [nodelist]
  "Given a list of nodes, return a list of all node ids"
  (map #(get (second %) :id) nodelist))

(defn- transformer
  [ast]
  (let [node-list (insta/transform {:enum to-enum
                                    :annotation to-annotation
                                    :documentation-annotation to-documentation-annotation
                                    :arity-value #(if (not= "n" %) (read-string %) %)
                                    :kind to-kind
                                    :type (abstract-to-type :type)
                                    :parent to-parent
                                    :interface (abstract-to-type :interface)
                                    :attribute to-attribute
                                    :method to-method
                                    :diagram to-diagram
                                    :diagram-group to-diagram-group} ast)]
    {:nodes (zipmap (id-list (filter type-interface-or-enum? node-list)) (filter type-interface-or-enum? node-list))
     :diagrams (zipmap (id-list (filter diagram? node-list)) (filter diagram? node-list))}))

(defn parse
  [content]
  (let [parsed (parser content)]
    (when (insta/get-failure parsed)
      (throw (Exception. (with-out-str (println (insta/get-failure parsed))))))
    (transformer parsed)))
