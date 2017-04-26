(ns umlaut.generators.lacinia
  (:require [clojure.java.io :as io]
            [umlaut.utils :refer :all]
            [umlaut.core :as core]
            [umlaut.models :as model]))
(use '[clojure.pprint :only [pprint]])

(def space "lang/lacinia")

(defn- prepend [symb curr]
  "Creates a new list with symb at the first position."
  (list (symbol symb) curr))

(defn- process-lacinia-type [attr type]
  "Converts type into a keyword if the type is not a primitive."
  (if (primitive? (attr :type-id))
    type
    (keyword type)))

(defn- convert-type [type]
  "Converts umlaut names to Lacinia names."
  (case type
    "Integer" 'Int
    (symbol type)))

(defn- lacinia-type [attr]
  "Transform a umlaut type into a lacinia type."
  (let [type (process-lacinia-type attr (convert-type (attr :type-id)))]
    (if (attr :required)
      (prepend "non-null" type)
      type)))

(defn- process-variable [attr]
  "Uses :artiy to determine if the variable is a list and builds the proper lacinia structure."
  (if (= (attr :arity) [1 1])
    {:type (lacinia-type attr)}
    (if (attr :required)
      {:type (prepend "non-null" (prepend "list" (lacinia-type attr)))}
      {:type (prepend "list" (prepend "non-null" (lacinia-type attr)))})))

(defn- process-params [params]
  "Builds the lacinia args structure "
  (reduce (fn [acc param]
            (merge acc {(keyword (param :id)) (process-variable param)}))
          {} params))

(defn- check-add-field-documentation [field out]
  (if (contains? (field :field-annotations) :documentation)
    (merge out {:description ((field :field-annotations) :documentation)})
    out))

(defn- check-add-field-deprecation [field out]
  (if (contains? (field :field-annotations) :deprecation)
    (merge out {:isDeprecated true :deprecationReason ((field :field-annotations) :deprecation)})
    (merge out {:isDeprecated false})))

(defn- check-add-params [field out]
  (if (field :params?)
    (merge out {:args (process-params (field :params))})
    out))

(defn- check-add-resolver [field out]
  (if (contains? (field :field-annotations) :others)
    (let [resolver (annotations-by-space-key space "resolver" ((field :field-annotations) :others))]
      (if (> (count resolver) 0)
        (merge out {:resolve (keyword ((first resolver) :value))})
        out))
    out))

(defn- process-field [field]
  "Receives a method and add an entry in the fields map"
  (->> (process-variable (field :return))
    (check-add-field-documentation field)
    (check-add-field-deprecation field)
    (check-add-params field)
    (check-add-resolver field)))

(defn- process-declaration [info]
  "Thread several reduces to build a map of types, args, and resolvers"
  (as-> info acc
    (reduce (fn [acc method]
              (merge acc {(keyword (method :id)) (process-field method)}))
            {} (info :fields))))

(defn- attr-to-values [info]
  (vec (info :values)))

(defn- attr-to-parents [info]
  (vec (map lacinia-type (info :parents))))

(defn- check-add-documentation [node out]
  (let [docs (first (annotations-by-space :documentation (node :annotations)))]
    (if docs
      (merge out {:description (:value docs)})
      out)))

(defn- check-add-deprecation [node out]
  (let [deprecation (first (annotations-by-space :deprecation (node :annotations)))]
    (if deprecation
      (merge out {:isDeprecated true :deprecationReason (deprecation :value)})
      (merge out {:isDeprecated false}))))

(defn- gen-node-type [node]
  (when (= (first node) :type)
    (let [info (second node)]
      (->> {(keyword (info :id)) {:fields (process-declaration info)
                                  :implements (attr-to-parents info)}}
           (check-add-documentation info)
           (check-add-deprecation info)))))

(defn- gen-node-enum [node]
  (when (= (first node) :enum)
    (let [info (second node)]
      (->> {(keyword (info :id)) {:values (attr-to-values info)}}
          (check-add-documentation info)
          (check-add-deprecation info)))))

(defn- gen-node-interface [node]
  (when (= (first node) :interface)
    (let [info (second node)]
      (->> {(keyword (info :id)) {:fields (process-declaration info)}}
          (check-add-documentation info)
          (check-add-deprecation info)))))

(defn- gen-query-type [node]
  (let [info (second node)]
    (->> (process-declaration info)
      (check-add-documentation info)
      (check-add-deprecation info))))

(defn- annotation-comprarer [key value]
  (fn [node]
    (let [annotations (annotations-by-space space ((last (last node)) :annotations))]
      (> (->> annotations
          (filter #(and (= value (% :value)) (= "identifier" (% :key))))
          (count)) 0))))

(defn- filter-input-nodes [nodes]
  (filter (annotation-comprarer "identifier" "input") nodes))

(defn- filter-mutation-nodes [nodes]
  (filter (annotation-comprarer "identifier" "mutation") nodes))

(defn- filter-query-nodes [nodes]
  (filter (annotation-comprarer "identifier" "query") nodes))

(defn- build-ignored-list [nodes]
  (flatten (list
            (map first (filter-input-nodes nodes))
            (map first (filter-mutation-nodes nodes))
            (map first (filter-query-nodes nodes)))))

(defn- filter-other-nodes [nodes]
  (let [all (build-ignored-list nodes)]
    (filter #(not (in? (first %) all)) nodes)))

(defn gen [files]
  "Returns a clojure map that can be used as a EDN schema"
  (let [umlaut (core/main files)
        nodes-seq (seq (umlaut :nodes))]
    (as-> nodes-seq coll
      (reduce
        (fn [acc [key node]]
          (merge acc {:objects (or (merge (acc :objects) (gen-node-type node)) {})
                      :enums (or (merge (acc :enums) (gen-node-enum node)) {})
                      :interfaces (or (merge (acc :interfaces) (gen-node-interface node)) {})}))
        {} (filter-other-nodes nodes-seq))
      (reduce
        (fn [acc [key node]]
          (merge acc {
                      :input-objects (or (merge (acc :input-objects) (gen-node-type node)) {})}))
        coll (filter-input-nodes nodes-seq))
      (reduce
        (fn [acc [key node]]
          (merge acc {
                      :mutations (or (merge (acc :mutations) (gen-query-type node)) {})}))
        coll (filter-mutation-nodes nodes-seq))
      (reduce
        (fn [acc [key node]]
          (merge acc {
                      :queries (or (merge (acc :queries) (gen-query-type node)) {})}))
        coll (filter-query-nodes nodes-seq)))))

