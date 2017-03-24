(ns umlaut.utils
    (:require [clojure.string :as string]
      [clojure.spec.gen :as gen]))

(def primitive-types ["String" "Float" "Integer" "Boolean" "DateTime"])

(defn primitive? "Whether type is primitive or not" [type]
      (> (count (filter #(=
                          (string/lower-case %)
                          (string/lower-case type)) primitive-types)) 0))

(defn not-primitive? "Whether type is not a primitive" [type]
      (not (primitive? type)))

(defn diagram?
  "Whether an AST node is a diagram node or not"
  [node]
  (= (first node) :diagram))

(defn type?
  "Whether an AST node is a type node or not"
  [node]
  (= (first node) :type))

(defn interface?
  "Whether an AST node is an interface node or not"
  [node]
  (= (first node) :interface))

(defn type-or-interface?
  "Whether an AST node is an interface node or not"
  [node]
  (or (type? node) (interface? node)))

(defn extend-key
  "Extends the key of a map with new values"
  [key new parent]
  (merge (parent key) new))

(defn umlaut-base [nodes diagrams]
  "Base map structure of the ast data structure"
  {:nodes nodes :diagrams diagrams})

(defn in?
  "Whether collection has the element inside"
  [element collection]
  (some #(= element %) collection))