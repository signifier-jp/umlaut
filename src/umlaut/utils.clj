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