(ns umlaut.models
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

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

(s/def ::attribute (s/keys :req-un [::id ::type-id ::arity]))
(s/def ::attributes (s/coll-of ::attribute :distinct true :into []))

(s/def ::parent (s/keys :req-un [::type-id]))
(s/def ::parents (s/coll-of ::parent :distinct true :into []))

(s/def ::type (s/keys :req-un [::id ::attributes ::parents]))
(s/def ::type-obj (s/keys :req-un [::type]))

(s/def ::interface (s/keys :req-un [::id ::attributes ::parents]))
(s/def ::interface-obj (s/keys :req-un [::interface]))

(s/def ::values (s/coll-of string? :distinct true :into '()))
(s/def ::enum (s/keys :req-un [::id ::values]))
(s/def ::enum-obj (s/keys :req-un [::enum]))

(s/def ::group (s/coll-of string? :kind vector? :distinct true :into []))
(s/def ::groups (s/coll-of ::group :distinct true :into []))
(s/def ::diagram (s/keys :req-un [::id ::groups]))
(s/def ::diagram-obj (s/keys :req-un [::diagram]))

(s/def ::obj (s/or :enum ::enum-obj :type ::type-obj :interface ::interface-obj :diagram ::diagram-obj))
(s/def ::objs (s/coll-of ::obj :into '()))
(s/def ::namespaces (s/map-of string? ::objs))