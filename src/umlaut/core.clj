(ns umlaut.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [umlaut.parser :refer [parse]]
            [umlaut.models :as model]
            [umlaut.utils :as utils]
            [clojure.spec :as s]
            [clojure.spec.test :as stest])
  (:gen-class))
(use '[clojure.pprint :only [pprint]])

(defn- id-list [nodelist]
  "Given a list of nodes, return a list of all node ids"
  (map #(get (second %) :id) nodelist))

(defn transform-structure [structure]
  "Receives a structure in this format:
  {
    'filename' (list of ast nodes)
    ...
    'filenameN' (list of ast nodes)
  }
  And transforms to this structure:
  {
    :nodes {
      'nodeId' astNode
      ...
    }
    :diagrams {
      'diagramId' astNode
      ...
    }
  }
  This bring us several benefits, for example, constant access time
  to AST nodes and isolation of definition blocks and other blocks,
  future nodes that could be added to the language shouldn't alter
  the :nodes key, ensuring backwards compatibility of all the generators."
  (reduce (fn [acc filename]
            (let [nodes (filter utils/not-diagram? (get structure filename))
                  diagrams (filter utils/diagram? (get structure filename))]
              (merge acc {:nodes (utils/extend-key :nodes (zipmap (id-list nodes) nodes) acc)
                          :diagrams (utils/extend-key :diagrams (zipmap (id-list diagrams) diagrams) acc)})))
          {} (keys structure)))

(defn- read-parse [path]
  "Read all the umlaut files from a folder and parse its content"
  (->> path
      (io/file)
      (file-seq)
      (map #(.getPath %))
      (filter #(str/ends-with? % ".umlaut"))
      (map (fn [x] {:file-name x :content (slurp x)}))
      (reduce
        #(assoc %1 (:file-name %2) (parse (:content %2)))
        '{})))

(defn- read-folder [path]
  "Validate all the umlaut code parsed from a folder"
  (let [parsed (read-parse path)]
    (if (not (s/valid? ::model/namespaces parsed))
      (throw (Exception. (s/explain ::model/namespaces parsed)))
      parsed)))

(defn -main
  "Parses, validates, and transform the umlaut files from a folder"
  [path]
  (transform-structure (read-folder path)))

(s/fdef -main
        :args (s/cat :path string?)
        :ret ::model/namespaces)

(stest/instrument `-main)
; (pprint (-main "test/sample"))

