(ns umlaut.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [umlaut.parser :refer [parse]]
            [umlaut.models :as model]
            [clojure.spec :as s]
            [clojure.spec.test :as stest])
  (:gen-class))

(defn -main
  [path & args]
  (->> path
       (io/file)
       (file-seq)
       (map #(.getPath %))
       (filter #(str/ends-with? % ".umlaut"))
       (map (fn [x] {:file-name x :content (slurp x)}))
       (reduce
         #(assoc %1 (:file-name %2) (parse (:content %2)))
         '{})))

(s/fdef -main
        :args (s/cat :path string?)
        :ret ::model/namespaces)

(stest/instrument `-main)

(def m (-main "test/sample"))

(s/valid? ::model/namespaces m)
(s/explain ::model/namespaces m)
