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

(defn- process-field [field]
  "Receives a method and add an entry in the fields map"
  (if (field :params?)
    (merge {} (process-variable (field :return)) {:args (process-params (field :params))})
    (process-variable (field :return))))

(defn- process-declaration [info]
  "Thread several reduces to build a map of types, args, and resolvers"
  (as-> info acc
    (reduce (fn [acc method]
              (merge acc {(keyword (method :id)) (process-field method)}))
            {} (info :fields))
    (reduce (fn [acc annotation]
              (let [key (keyword (first (annotation :value)))]
                (merge acc {key (merge (acc key) {:resolve (keyword (second (annotation :value)))})})))
            acc (annotations-by-space-key space "resolver" (info :annotations)))))

(defn- attr-to-values [info]
  (vec (info :values)))

(defn- attr-to-parents [info]
  (vec (map lacinia-type (info :parents))))

(defn- gen-node-type [node]
  (when (= (first node) :type)
    (let [info (second node)]
      (assoc {} (keyword (info :id))
          {:fields (process-declaration info)
           :implements (attr-to-parents info)}))))

(defn- gen-node-enum [node]
  (when (= (first node) :enum)
    (let [info (second node)]
      (assoc {} (keyword (info :id))
                {:values (attr-to-values info)}))))

(defn- gen-node-interface [node]
  (when (= (first node) :interface)
    (let [info (second node)]
      (assoc {} (keyword (info :id)) {:fields (process-declaration info)}))))

(defn- gen-query-type [node]
  (let [info (second node)]
    (merge {} (process-declaration info))))

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

(defn gen
  [umlaut]
  (let [nodes-seq (seq (umlaut :nodes))]
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
                      :mutations (or (merge (acc :mutations) (gen-node-type node)) {})}))
        coll (filter-mutation-nodes nodes-seq))
      (reduce
        (fn [acc [key node]]
          (merge acc {
                      :queries (or (merge (acc :queries) (gen-query-type node)) {})}))
        coll (filter-query-nodes nodes-seq)))))

(defn gen-lacinia
  [path]
  (gen (core/main path)))

; (pprint (gen-lacinia ["test/fixtures/person/person.umlaut" "test/fixtures/person/profession.umlaut"]))
