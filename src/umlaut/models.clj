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

(defn- create-type-id [type-id def-name]
  (fn [] (s/with-gen
           (s/def def-name #(= type-id %))
           #(gen/fmap (fn [& _] type-id)))))

(s/def ::attribute (s/keys :req-un [::id ::type-id ::arity ::required]))
(s/def ::attributes (s/coll-of ::attribute :distinct true :into []))

(s/def ::parent (s/keys :req-un [::type-id]))
(s/def ::parents (s/coll-of ::parent :distinct true :into []))

(s/def ::annotation (s/keys :req-un [::space ::key ::value]))
(s/def ::annotations  (s/coll-of ::annotation :distinct true :into []))

(s/def ::method (s/keys :req-un [::id ::return ::params]))
(s/def ::return (s/keys :req-un [::type-id ::required]))
(s/def ::methods  (s/coll-of ::method :distinct true :into []))

(s/def ::type (s/keys :req-un [::id ::attributes ::parents ::annotations ::methods]))
(s/def ::type-kw (s/with-gen #(= :type %) #(s/gen #{:type})))
(s/def ::type-obj (s/tuple ::type-kw ::type))

(s/def ::interface (s/keys :req-un [::id ::attributes ::parents ::annotations ::methods]))
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



; (def model {:nodes
;              {"t2"
;               [:type
;                {:id "t2",
;                 :attributes
;                 [{:type-id "String", :arity [1 1], :id "id", :required true}
;                  {:type-id "String", :arity [1 1], :id "id2", :required false}],
;                 :methods
;                 [{:id "myMethod2",
;                   :return {:type-id "ReturnString", :required true},
;                   :params
;                   [{:type-id "String",
;                     :arity [1 1],
;                     :id "parameter",
;                     :relationship-type :attribute,
;                     :required false}]}
;                  {:id "myMethod",
;                   :return {:type-id "ReturnString", :required false},
;                   :params
;                   [{:type-id "String",
;                     :arity [1 1],
;                     :id "parameter",
;                     :relationship-type :attribute,
;                     :required false}
;                    {:type-id "Integer",
;                     :arity [1 1],
;                     :id "param2",
;                     :relationship-type :attribute,
;                     :required false}]}
;                  {:id "myMethod3",
;                   :return {:type-id "ReturnType", :required false},
;                   :params
;                   [{:type-id "String",
;                     :arity [1 1],
;                     :id "p1",
;                     :relationship-type :attribute,
;                     :required false}
;                    {:type-id "Integer",
;                     :arity [1 1],
;                     :id "p2",
;                     :relationship-type :attribute,
;                     :required false}
;                    {:type-id "Boolean",
;                     :arity [1 1],
;                     :id "P3",
;                     :relationship-type :attribute,
;                     :required true}]}],
;                 :parents [],
;                 :annotations []}],
;               "A"
;               [:interface
;                {:id "A",
;                 :attributes [],
;                 :methods
;                 [{:id "m",
;                   :return {:type-id "Void", :required false},
;                   :params
;                   [{:type-id "String",
;                     :arity [1 1],
;                     :id "abc",
;                     :relationship-type :attribute,
;                     :required false}]}],
;                 :parents [],
;                 :annotations []}],
;               "t1"
;               [:type
;                {:id "t1",
;                 :attributes
;                 [{:type-id "String", :arity [1 1], :id "id3", :required false}],
;                 :methods [],
;                 :parents [],
;                 :annotations
;                 [{:space "lang/graphql", :key "identifier", :value "input"}
;                  {:space "lang/java", :key "identifier", :value "input"}]}]},
;              :diagrams {}})
;
; (println (s/valid? ::namespaces model))
; (println (s/explain ::namespaces model))
