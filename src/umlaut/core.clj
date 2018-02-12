(ns umlaut.core
  (:gen-class)
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [umlaut.models :as model]
            [umlaut.parser :refer [parse]]
            [umlaut.utils :refer [in? primitive-types]]))

(defn- read-parse [path]
  "Read all the umlaut files from a folder and parse its content"
  (->> path
       (list)
       (flatten)
       (reduce (fn [acc filename]
                 (let [parsed (parse (slurp filename))]
                   {:nodes (merge (:nodes acc) (:nodes parsed))
                    :diagrams (merge (:diagrams acc) (:diagrams parsed))})) {})))

(defn- get-all-fields
  [parsed]
  (map (fn [[key [kind type-obj]]]
         :fields (:fields type-obj))
       (seq (:nodes parsed))))

(defn- check-field-type
  [valid-types field]
  (let [field-type (:type-id (:return field))]
    (if (not (in? field-type valid-types))
      (str "Field with invalid type: '" (:id field) "' cannot be '" field-type "'")
      nil)))

(defn- validate-types
  [parsed]
  (let [declared-types (keys (:nodes parsed))
        valid-types (concat declared-types primitive-types)
        all-fields (get-all-fields parsed)]
    (remove nil?
            (reduce
             (fn [acc fields]
               (concat acc (map (partial check-field-type valid-types) fields)))
             []
             all-fields))))

(defn- check-throw-spec-error
  [parsed]
  (if (not (s/valid? ::model/namespaces parsed))
    (throw (Exception. (s/explain ::model/namespaces parsed)))
    parsed))

(defn- format-type-errors
  [errors]
  (reduce
   (fn [acc error]
     (str acc error "\n"))
   "\n"
   errors))

(defn- check-throw-type-error
  [parsed]
  (let [result (validate-types parsed)]
    (if (> (count result) 0)
      (throw (Exception. (format-type-errors result)))
      parsed)))

(defn- read-folder [path]
  "Validate all the umlaut code parsed from a folder"
  (-> (read-parse path)
      check-throw-spec-error
      check-throw-type-error))

(defn main
  "Parses, validates, and transform the umlaut files from a folder"
  [path]
  (read-folder path))

(s/fdef main
        :ret ::model/namespaces)

(stest/instrument `main)
