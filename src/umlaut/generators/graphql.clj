(ns umlaut.generators.graphql
  (:require [clojure.java.io :as io]
            [umlaut.utils :refer :all]
            [umlaut.core :as core]
            [clojure.string :as string]))
(use '[clojure.pprint :only [pprint]])

(defn- gen-documentation [node]
  (let [docs (first (annotations-by-space :documentation (node :annotations)))]
    (if docs
      (str "# " (docs :value) "\n")
      "")))

(defn- gen-field-documentation [field]
  (if (contains? (field :field-annotations) :documentation)
    (str "  # " ((field :field-annotations) :documentation) "\n")
    ""))

(defn- gen-required [type-obj]
  "Checks required attribute and returns ! or not"
  (if (type-obj :required)
    "!"
    ""))

(defn- contain-parents? [node]
  "Whether the type inherits from other types or not"
  (and (contains? node :parents) (> (count (get node :parents)) 0)))

(defn- check-arity [[from to]]
  "Returns a boolean indicating if we should represent arity in the graphql"
  (= from to))

(defn- gen-type-with-arity [type-obj]
  "Builds the field type properly considering arity"
  (if (check-arity (type-obj :arity))
    (type-obj :type-id)
    (str "[" (type-obj :type-id) "!]")))

(defn- gen-field-type-label [type-obj]
  "Helper function that concatenates arity + required"
  (str (gen-type-with-arity type-obj) (gen-required type-obj)))

(defn- gen-field-type-without-params [field]
  "A field without parameters (attribute) is type with arity + required"
  (gen-field-type-label (field :return)))

(defn- gen-field-parameters [field]
  "Iterates over the parameters reusing gen-field-type-label"
  (string/join ", " (map #(str (% :id) ": " (gen-field-type-label %)) (field :params))))

(defn- gen-field-type [field]
  "Build the graphql notation for methods and attributes"
  (if (field :params?)
    (str "(" (gen-field-parameters field) "): " (gen-field-type-label (field :return)))
    (str ": " (gen-field-type-without-params field))))

(defn- gen-field
  [field]
  (str
    (gen-field-documentation field)
    "  " (field :id) (gen-field-type field) "\n"))

(defn- gen-fields
  [fields]
  (reduce (fn [acc field]
            (str acc (gen-field field)))
    "" fields))

(defn- gen-enum-values
  [values]
  (reduce (fn [a value] (str a (str "  " value "\n")))
          "" values))

(defn- get-annotations [node]
  (filter #(= (% :space) "lang/graphql") (node :annotations)))

(defn- get-identifier-annotation [annotations]
  (or (first (filter #(= (% :key) "identifier") annotations)) []))

(defn- gen-identifier [kind-str node]
  (let [annotations (annotations-by-space-key "lang/graphql" "identifier" (node :annotations))]
    (if (> (count annotations) 0)
      (if (= (count annotations) 1)
        ((first annotations) :value)
        (do
          (println (str "WARNING: More than one identifier annotation found for declaration block " (node :id)))
          ((first annotations) :value)))
      kind-str)))

(defn- gen-identifier-label [kind-str node]
  (let [identifier (gen-identifier kind-str node)]
    (if (= identifier "schema")
      identifier
      (str identifier " " (node :id)))))

(defn- gen-entry-enum [body]
  (str
    (gen-documentation body)
    "enum " (:id body) " {\n"
     (gen-enum-values (:values body))
    "}\n\n"))

(defn- gen-entry-others [kind-str body]
  (str
    (gen-documentation body)
    (gen-identifier-label kind-str body)
    (when (contain-parents? body)
      (str " implements " (string/join "," (map #(% :type-id) (body :parents)))))
    " {\n"
      (gen-fields (body :fields))
    "}\n\n"))

(defn- gen-entry
  [[kind node]]
  (case kind
    :type (gen-entry-others "type" node)
    :interface (gen-entry-others "interface" node)
    :enum (gen-entry-enum node)
    ""))

(defn gen [files]
  "Returns a valid graphQL schema string"
  (let [umlaut (umlaut.core/main files)]
    (reduce (fn [acc [key node]]
              (str acc (gen-entry node)))
            "" (seq (umlaut :nodes)))))

