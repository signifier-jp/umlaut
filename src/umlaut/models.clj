(ns umlaut.models
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

(defn- positive? [f] (and (number? f) (>= f 0)))

(defn- arity-generator []
  (gen/fmap (fn [[from off n?]]
              (let [f (max from (- from))
                    o (max off (- off))]
                (vector f (if n? "n" (+ f o)))))
            (gen/tuple (gen/int) (gen/int) (gen/boolean))))

(s/def ::id (s/and string? #(> (count %) 0)))
(s/def ::type-id (s/and string? #(> (count %) 0)))
(s/def ::arity (s/with-gen
                 (s/and
                  (s/tuple positive? #(if (= "n" %) true (positive? %)))
                  (fn [[from to]] (if (not= "n" to) (>= to from) true)))
                 arity-generator))

(defn- create-type-id [type-id def-name]
  (fn [] (s/with-gen
           (s/def def-name #(= type-id %))
           #(gen/fmap (fn [& _] type-id)))))

(s/def ::attribute (s/keys :req-un [::id ::type-id ::arity ::required]))
(s/def ::parent (s/keys :req-un [::type-id]))
(s/def ::parents (s/coll-of ::parent :distinct true :into []))

(s/def ::annotation (s/keys :req-un [::space ::key ::value]))
(s/def ::annotations  (s/coll-of ::annotation :distinct true :into []))
(s/def ::field-obj (s/or :others (s/coll-of ::annotation :distinct true :into [])
                         :documentation string?
                         :deprecation string?))
(s/def ::field-annotations  (s/map-of keyword? ::field-obj))

(s/def ::method (s/keys :req-un [::id ::return ::params ::params? ::field-annotations]))
(s/def ::return (s/keys :req-un [::type-id ::required ::arity]))
(s/def ::fields  (s/coll-of ::method :distinct true :into []))

(s/def ::type (s/keys :req-un [::id ::parents ::annotations ::fields]))
(s/def ::type-kw (s/with-gen #(= :type %) #(s/gen #{:type})))
(s/def ::type-obj (s/tuple ::type-kw ::type))

(s/def ::interface (s/keys :req-un [::id ::parents ::annotations ::fields]))
(s/def ::interface-kw (s/with-gen #(= :interface %) #(s/gen #{:type})))
(s/def ::interface-obj (s/tuple ::interface-kw ::interface))

(s/def ::values (s/coll-of string? :distinct true :into '()))
(s/def ::enum (s/keys :req-un [::id ::values]))
(s/def ::enum-kw (s/with-gen #(= :enum %) #(s/gen #{:enum})))
(s/def ::enum-obj (s/tuple ::enum-kw ::enum))

(s/def ::group (s/coll-of string? :kind vector? :distinct true :into []))
(s/def ::groups (s/coll-of ::group :distinct true :into []))
(s/def ::diagram (s/keys :req-un [::id ::groups]))
(s/def ::diagram-kw (s/with-gen #(= :diagram %) #(s/gen #{:diagram})))
(s/def ::diagram-obj (s/tuple ::diagram-kw ::diagram))

(s/def ::obj (s/or :enum ::enum-obj
                   :type ::type-obj
                   :interface ::interface-obj
                   :diagram ::diagram-obj))
(s/def ::objs (s/map-of string? ::obj))
(s/def ::namespaces (s/map-of keyword? ::objs))
