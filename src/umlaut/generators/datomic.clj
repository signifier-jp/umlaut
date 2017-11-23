(ns umlaut.generators.datomic
  (:require [camel-snake-kebab.core :refer [->kebab-case-string]]
            [umlaut.core :as core]))

(defn ^:private is-type-or-enum? [[_ [node-type node-info]]]
  (or (= :type node-type)
      (= :enum node-type)))

(defn ^:private has-simple-fields? [[_ [node-type node-info]]]
  (if (or (= :type node-type)
          (= :interface node-type))
    (reduce (fn [a i]
              (println (:params? i))
              (or a (not (:params? i))))
            false
            (:fields node-info))
    true))

(defn ^:private get-space-value [{{:keys [others]} :field-annotations} k]
  (some->> others
           (filter #(and (= "lang/datomic" (:space %))
                         (= k (:key %))))
           first
           :value))

(defmulti ^:private gen-return (fn [field] (-> field :return :type-id)))

(defmethod gen-return "Boolean" [_] :db.type/boolean)

(defmethod gen-return "DateTime" [_] :db.type/instant)

(defmethod gen-return "ID" [_] :db.type/uuid)

(defmethod gen-return "String" [field]
  (let [precision (get-space-value field "precision")]
    (cond
      (= "keyword" precision) :db.type/keyword
      (= "uri" precision) :db.type/uri
      :else :db.type/string)))

(defmethod gen-return "Float" [field]
  (let [precision (get-space-value field "precision")]
    (cond
      (= "double" precision) :db.type/double
      (= "bigdec" precision) :db.type/bigdec
      :else :db.type/float)))

(defmethod gen-return "Integer" [field]
  (let [precision (get-space-value field "precision")]
    (cond
      (= "bigint" precision) :db.type/bigint
      :else :db.type/long)))

(defmethod gen-return :default [_] :db.type/ref)

(defn ^:private gen-cardinality [{{:keys [arity]} :return}]
  (if (and (= (first arity) 1)
           (= (second arity) 1))
    :db.cardinality/one
    :db.cardinality/many))

(defn ^:private assoc-unique [m field]
  (let [unique (get-space-value field "unique")]
    (cond-> m
      (= unique "value") (assoc :db/unique :db.unique/value)
      (= unique "identity") (assoc :db/unique :db.unique/identity))))

(defn ^:private assoc-index [m field]
  (let [index (get-space-value field "index")]
    (cond-> m
      (= index "true") (assoc :db/index true))))

(defn ^:private assoc-fulltext [m field]
  (let [unique (get-space-value field "fulltext")]
    (cond-> m
      (= unique "true") (assoc :db/fulltext true))))

(defn ^:private assoc-doc [m {{:keys [documentation]} :field-annotations}]
  (if documentation
    (assoc m :db/doc documentation)
    m))

(defn ^:private process-field [entity-id field]
  (-> {:db/ident (keyword entity-id
                          (->kebab-case-string (:id field)))
       :db/valueType (gen-return field)
       :db/cardinality (gen-cardinality field)}
      (assoc-unique field)
      (assoc-index field)
      (assoc-fulltext field)
      (assoc-doc field)))

(defmulti ^:private gen-node (fn [node-type node-info] node-type))

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

(defn gen [files]
  "Returns a clojure vector in the datomic schema format"
  (let [umlaut (resolve-inheritance (core/main files))
        nodes (->> (:nodes umlaut)
                   (filter is-type-or-enum?)
                   (filter has-simple-fields?)
                   (sort-by #(first %)))]
    (reduce (fn [c [_ [node-type node-info]]]
              (concat c (gen-node node-type node-info)))
            []
            nodes)))
