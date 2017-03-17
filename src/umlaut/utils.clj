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

(defn not-diagram?
  "Complement of diagram?"
  [node]
  (not (diagram? node)))

(defn extend-key
  "Extends the key of a map with new values"
  [key new parent]
  (merge (parent key) new))
