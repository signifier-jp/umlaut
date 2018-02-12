(ns umlaut.generators.spec
  (:require [camel-snake-kebab.core :refer [->kebab-case-string]]
            [umlaut.core :as core]
            [umlaut.utils :refer [annotations-by-space-key
                                  primitive?
                                  interface?
                                  in?
                                  not-primitive?
                                  union?
                                  resolve-inheritance]]))

(def ^:private primitive-predicates
  {"ID" 'string?
   "String" 'string?
   "Float" 'float?
   "Integer" 'integer?
   "Boolean" '#(contains? #{true false} %)
   "DateTime" 'string?})

(defn- build-qualified-keyword [type-obj field opts]
  "Builds a fully qualified keyword given a type and a field"
  (keyword
   (str
    (if (= (:type-id (:return field)) "ID")
      (->kebab-case-string (:id-namespace opts))
      (->kebab-case-string (:id type-obj)))
    "/"
    (->kebab-case-string (:id field)))))

(defn- custom-validator-wrap-code [base-validator validator-name]
  "Function that wraps a base validator and a custom validator name in a s/and statement. base-validator should be
  passed with (partial name arg1 arg2 ...) so this function can call without passing any other parameters."
  (list 's/and (base-validator) (symbol validator-name)))

(defn- interface-wrap-code [concrete-types opts]
  "Function that wraps a base validator and a custom validator name in a s/and statement. base-validator should be
  passed with (partial name arg1 arg2 ...) so this function can call without passing any other parameters."
  (concat
   (list 's/or)
   (->> concrete-types
        (map ->kebab-case-string)
        (map (fn [type]
               (list (keyword type) (build-qualified-keyword {:id type} {:id type} opts))))
        (flatten))))

(defn- get-annotation-identifier [annotation]
  "Safe check before returning"
  (if annotation
    (:value annotation)
    annotation))

(defn- get-type-validator-identifier [type-obj]
  "Get a possible validator identifier from a type or enum"
  (->> (:annotations type-obj)
       (annotations-by-space-key "lang/spec" "validator")
       (first)
       (get-annotation-identifier)))

(defn- get-field-validator-identifier [field]
  "Get a possible validator identifier from a field"
  (as-> (:field-annotations field) result
        (:others result)
        (or result [])
        (annotations-by-space-key "lang/spec" "validator" result)
        (first result)
        (get-annotation-identifier result)))

(defn- namespace-from-type [type-obj]
  "Converts the type-obj :id to kebab notation"
  (->kebab-case-string (:id type-obj)))

(defn- full-namespace [type-obj package]
  "Concatenates the package with the type-obj :id"
  (str package "." (namespace-from-type type-obj)))

(defn- is-interface? [type-id opts]
  "Checks whether a type-id is an interface or not"
  (if (primitive? type-id)
    false
    (interface? (get (:nodes (:umlaut opts)) type-id))))

(defn- get-all-field-ids [type-obj]
  "Get an array of strings with the type-id of all the fields of a type"
  (map #(:type-id (:return %)) (:fields type-obj)))

(defn- get-interface-fields [type-obj opts]
  "Get all interface fields"
  (filter #(is-interface? % opts) (get-all-field-ids type-obj)))

(defn- get-other-fields [type-obj opts]
  "Get all the fields that are not an interface"
  (filter #(not (is-interface? % opts)) (get-all-field-ids type-obj)))

(defn- is-parent [interface parents]
  (in? interface (map #(:type-id %) parents)))

(defn- get-concrete-types [interface opts]
  "Given an interface name (type-id) return all the types that implement that interface"
  (->>
   (seq (:nodes (:umlaut opts)))
   (map (fn [[k [kind node]]]
          (if (is-parent interface (:parents node))
            node
            nil)))
   (filter #(not (nil? %)))
   (map #(:id %))))

(defn- resolve-type-dependencies [type-obj opts]
  "The dependencies of a type are all the fields that are not primitive and the types that implement the interfaces"
  (filter not-primitive?
          (concat
           (get-other-fields type-obj opts)
           (mapcat (fn [interface]
                     (get-concrete-types interface opts))
                   (get-interface-fields type-obj opts)))))

(defn- build-type-dependencies [type-obj opts]
  "Add all type dependencies for a type. Interfaces are replaced by the types that implements it"
  (distinct
   (map (fn [type]
          [(symbol (full-namespace {:id type} (:spec-package opts))) :refer :all])
        (resolve-type-dependencies type-obj opts))))

(defn- build-code-requires [type-obj opts]
  "Returns a list with all the required libraries for a given type or enum (unions)"
  (concat
   (list
    '[clojure.spec.alpha :as s]
    [(symbol (:validators-namespace opts)) :refer :all])
   (build-type-dependencies type-obj opts)
   (if (union? type-obj)
      ; Add the require for union specs
     (map (fn [value]
            [(symbol (full-namespace {:id value} (:spec-package opts))) :refer :all])
          (:values type-obj))
     ())))

(defn- build-code-header [type-obj opts]
  "The header is the namespace definition plus the required libraries"
  (list 'ns (symbol (full-namespace type-obj (:spec-package opts)))
        (concat
         (list ':require)
         (build-code-requires type-obj opts))))

(defn- get-field-type [field]
  "Returns the type of a field"
  (:type-id (:return field)))

(defn- get-field-arity [field]
  "Returns the arity of a field"
  (:arity (:return field)))

(defn- wrap-nilable [field expression]
  "Wrap the expression in a nilable spec if the field is optional"
  (if ((field :return) :required)
    expression
    (list 's/nilable expression)))

(defn- get-predicate [field opts]
  "Builds the predicate that validates the spec of a field"
  (let [type (get-field-type field)]
    (if (primitive? type)
      (primitive-predicates type)
      (build-qualified-keyword {:id type} {:id type} opts))))

(defn- get-min-max-count [field]
  "Returns a list with (:max-count x :min-count y) for a s/coll-of spec"
  (let [[from to] (get-field-arity field)
        lst (list ':min-count from)]
    (if (not= to "n")
      ; Conj adds to the beginnig of the list
      ; so we need to add 'to' first and then
      ; :max-count to build ':max-count to'
      (conj lst to ':max-count)
      lst)))

(defn- get-spec-validator [field opts]
  "Builds the validator of a spec based on its arity and type."
  (wrap-nilable
   field
   (let [[from to] (get-field-arity field)]
     (if (and (= from 1) (= to 1))
       (if (is-interface? (get-field-type field) opts)
         (interface-wrap-code (get-concrete-types (get-field-type field) opts) opts)
         (get-predicate field opts))
       (concat
        (list 's/coll-of
              (if (is-interface? (get-field-type field) opts)
                (interface-wrap-code (get-concrete-types (get-field-type field) opts) opts)
                (get-predicate field opts)))
        (get-min-max-count field))))))

(defn- get-validation-function [field opts]
  "Checks for a field annotation. If there is one, wraps the get-spec-validator in a s/and statement.
  if there isn't, just use the get-spec-validator"
  (let [validator (get-field-validator-identifier field)]
    (if validator
      (custom-validator-wrap-code (partial get-spec-validator field opts) validator)
      (get-spec-validator field opts))))

(defn- build-type-shape [shape-type relevant-fields type-obj opts]
  "Build the :req vector for all required fields of a type"
  (concat
   (list shape-type)
   (list
    (reduce
     (fn [acc field]
       (conj acc (build-qualified-keyword type-obj field opts)))
     [] relevant-fields))))

(defn- build-shape-validator [type-obj id-namespace]
  "Returns the spec for the entire types. This lists the required and optional fields.
  Can also be wrapped around a s/and statement if an annotation is present"
  (concat
   (list 's/keys)
   (build-type-shape :req
      ; Filter all the required fields
                     (filter #(:required (:return %)) (:fields type-obj)) type-obj id-namespace)
   (build-type-shape :opt
      ; Filter all the optional fields
                     (filter #(not (:required (:return %))) (:fields type-obj)) type-obj id-namespace)))

(defn- build-type-body [type-obj opts]
  "Returns the clojure code that defines a spec for each of the type's fields plus the shape validator"
  (let [validator (get-type-validator-identifier type-obj)]
    (concat
     (map (fn [field]
            (list 's/def
                  (build-qualified-keyword type-obj field opts)
                  (get-validation-function field opts)))
          (:fields type-obj))
     (list
      (list 's/def (build-qualified-keyword type-obj type-obj opts)
            (if validator
              (custom-validator-wrap-code (partial build-shape-validator type-obj opts) validator)
              (build-shape-validator type-obj opts)))))))

(defn- values-to-set [type-obj]
  "Returns a set from all the values in an enum type."
  (set (:values type-obj)))

(defn- build-enum-body [type-obj opts]
  "Builds the clojure code to validate an enum."
  (let [validator (get-type-validator-identifier type-obj)]
    (list 's/def (build-qualified-keyword type-obj type-obj opts)
          (if validator
            (custom-validator-wrap-code (partial values-to-set type-obj) validator)
            (values-to-set type-obj)))))

(defn- union-or [type-obj opts]
  "Returns a list of key value elements to be inserted inside the union-validator"
  (flatten
   (->> (:values type-obj)
        (map ->kebab-case-string)
        (map (fn [value]
               (list (keyword value) (build-qualified-keyword {:id value} {:id value} opts)))))))

(defn- union-validator [type-obj opts]
  "Returns a s/or statement with all the key value specs that are valid for the union"
  (concat
   (list 's/or)
   (union-or type-obj opts)))

(defn- build-union-body [type-obj opts]
  "Builds the code to validate an union. Takes annotation into consideration."
  (let [validator (get-type-validator-identifier type-obj)]
    (list 's/def (build-qualified-keyword type-obj type-obj opts)
          (if validator
            (custom-validator-wrap-code (partial union-validator type-obj opts) validator)
            (union-validator type-obj opts)))))

(defn- process-type [type-obj opts]
  "Builds the code to validate the spec of a type."
  (concat
   (list (build-code-header type-obj opts))
   (build-type-body type-obj opts)))

(defn- process-enum [type-obj opts]
  "Builds the code to validate the spec of an enum."
  (list
   (build-code-header type-obj opts)
   (if (union? type-obj)
     (build-union-body type-obj opts)
     (build-enum-body type-obj opts))))

(defn- process-node [[kind type-obj] opts]
  "Calls the appropriate function depending on the object kind. Interface does not generate
  spec because the fields were resolved with the resolve-inheritance function."
  (let [clojure-code
        (case kind
          :type (process-type type-obj opts)
          :interface {}
          :enum (process-enum type-obj opts))]
    (reduce
     (fn [acc x]
       (if (= x (last clojure-code))
         (str acc (with-out-str (clojure.pprint/pprint x)))
         (str acc (with-out-str (clojure.pprint/pprint x)) "\n")))
     "; AUTO-GENERATED: Do NOT edit this file\n" clojure-code)))

(defn gen [spec-package validators-namespace id-namespace files]
  "Returns a clojure map that the keys are file names, the value are clojure spec code"
  (println "Remember!")
  (println (str "You need to have a namespace: " validators-namespace " that defines your custom validators."))
  (let [umlaut (resolve-inheritance (core/main files))
        nodes-seq (seq (umlaut :nodes))
        opts {:spec-package spec-package
              :validators-namespace validators-namespace
              :id-namespace id-namespace
              :umlaut umlaut}]
    (reduce
     (fn [acc [key node]]
        ; node is a vector with 2 elements: kind and type-obj
       (if (= (first node) :interface)
         acc
         (merge acc {(namespace-from-type (second node))
                     (process-node node opts)})))
     {} nodes-seq)))

; (def out (gen "philz-api.specs" "philz-api.validators" "id" ["test/philz/main.umlaut"]))
; (clojure.pprint/pprint out)
; (clojure.pprint/pprint (out "test-a"))
; (spit "output/requires.clj" (out "test"))
; (clojure.pprint/pprint "--------------")
; (clojure.pprint/pprint (out "viewer-node"))
; (clojure.pprint/pprint "--------------")
; (clojure.pprint/pprint (out "carousel-favorite-node"))
