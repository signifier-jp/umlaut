(ns umlaut.generators.datomic
  (:require [camel-snake-kebab.core :refer [->kebab-case-string]]
            [umlaut.core :as core]
            [umlaut.utils :as utils]))

(defn ^:private is-type-or-enum?
  "Returns true if the passed node is a type or an enum."
  [[_ node]]
  (or (utils/type? node) (utils/enum? node)))

(defn ^:private has-simple-fields?
  "If the node passed is a type or interface, returns true if at least one field is
   free of params."
  [[_ node]]
  (if (or (utils/type? node)
          (utils/interface? node))
    (reduce (fn [a i]
              (or a (not (:params? i))))
            false
            (-> node second :fields))
    true))

(defn ^:private get-space-value
  "Given a field info and a key for the lang/datomic space, return its value."
  [{{:keys [others]} :field-annotations} k]
  (some->> others
           (utils/annotations-by-space-key "lang/datomic" k)
           first
           :value))

(defmulti ^:private gen-value-type
  "Given a field, returns the Datomic :db/valueType"
  (fn [field] (-> field :return :type-id)))

(defmethod gen-value-type "Boolean" [_] :db.type/boolean)

(defmethod gen-value-type "DateTime" [_] :db.type/instant)

(defmethod gen-value-type "ID" [_] :db.type/uuid)

(defmethod gen-value-type "String" [field]
  (let [precision (get-space-value field "precision")]
    (cond
      (= "keyword" precision) :db.type/keyword
      (= "uri" precision) :db.type/uri
      :else :db.type/string)))

(defmethod gen-value-type "Float" [field]
  (let [precision (get-space-value field "precision")]
    (cond
      (= "double" precision) :db.type/double
      (= "bigdec" precision) :db.type/bigdec
      :else :db.type/float)))

(defmethod gen-value-type "Integer" [field]
  (let [precision (get-space-value field "precision")]
    (cond
      (= "bigint" precision) :db.type/bigint
      :else :db.type/long)))

(defmethod gen-value-type :default [_] :db.type/ref)

(defn ^:private gen-cardinality
  "Given a field, returns the Datomic :db.cardinality"
  [{{:keys [arity]} :return}]
  (if (and (= (first arity) 1)
           (= (second arity) 1))
    :db.cardinality/one
    :db.cardinality/many))

(defn ^:private assoc-unique
  "Given a map and a field, returns a new map with the Datomic unique values if they
  have been defined."
  [m field]
  (let [unique (get-space-value field "unique")]
    (cond-> m
      (= unique "value") (assoc :db/unique :db.unique/value)
      (= unique "identity") (assoc :db/unique :db.unique/identity))))

(defn ^:private assoc-index
  "Given a map and a field, returns a new map with the Datomic index values if they
  have been defined."
  [m field]
  (let [index (get-space-value field "index")]
    (cond-> m
      (= index "true") (assoc :db/index true))))

(defn ^:private assoc-fulltext
  "Given a map and a field, returns a new map with the Datomic fulltext values if they
  have been defined."
  [m field]
  (let [unique (get-space-value field "fulltext")]
    (cond-> m
      (= unique "true") (assoc :db/fulltext true))))

(defn ^:private assoc-is-component
  "Given a map and a field, returns a new map with the Datomic isComponent values if they
  have been defined."
  [m field]
  (let [unique (get-space-value field "isComponent")]
    (cond-> m
            (= unique "true") (assoc :db/isComponent true))))

(defn ^:private assoc-doc
  "Given a map and a field, returns a new map with the Datomic :db.doc if they
  have been defined."
  [m {{:keys [documentation]} :field-annotations}]
  (if documentation
    (assoc m :db/doc documentation)
    m))

(defn ^:private process-field
  "Returns all the fields in Datomic schema format."
  [entity-id field]
  (-> {:db/ident (keyword entity-id
                          (->kebab-case-string (:id field)))
       :db/valueType (gen-value-type field)
       :db/cardinality (gen-cardinality field)}
      (assoc-unique field)
      (assoc-index field)
      (assoc-fulltext field)
      (assoc-is-component field)
      (assoc-doc field)))

(defmulti ^:private gen-node
  "Given a node type and a node info, return a collection with all the Datomic schema entries
  to define it."
  (fn [node-type node-info] node-type))

(defmethod gen-node :type [_ node-info]
  (let [entity-id (->kebab-case-string (:id node-info))
        fields (->> (:fields node-info)
                    (filter #(not (:params? %)))
                    (sort-by #(:id %)))]
    (reduce (fn [c field]
              (conj c (process-field entity-id field)))
            [] fields)))

(defmethod gen-node :enum [_ node-info]
  (let [entity-id (->kebab-case-string (:id node-info))
        values (->> (:values node-info)
                    (sort-by #(:id %)))]
    (map (fn [v] {:db/ident (keyword entity-id
                                     (->kebab-case-string v))})
         values)))

(defn gen
  "Returns a clojure vector in the Datomic schema format"
  [files]
  (let [umlaut (utils/resolve-inheritance (core/main files))
        nodes (->> (:nodes umlaut)
                   (filter is-type-or-enum?)
                   (filter has-simple-fields?)
                   (sort-by #(first %)))]
    (reduce (fn [c [_ [node-type node-info]]]
              (concat c (gen-node node-type node-info)))
            []
            nodes)))
