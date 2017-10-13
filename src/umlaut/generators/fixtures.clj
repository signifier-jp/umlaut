(ns umlaut.generators.fixtures
  (:require [umlaut.generators.dot :as dot]
            [umlaut.generators.graphql :as graphql]
            [umlaut.generators.lacinia :as lacinia]
            [umlaut.utils :refer [save-map-to-file
                                  save-string-to-file
                                  primitive?]]))

;; IMPORTANT!
;; This fixture generator should ONLY be used if you changed
;; the umlaut code inside the fixture folder. Changes in the
;; code required a manual fixture fix.

(def ^:private base "test/fixtures/person/")
(def ^:private umlaut-files [(str base "person.umlaut") (str base "profession.umlaut")])

(defn- gen-umlaut [filename files]
  (save-map-to-file filename (umlaut.core/main files)))

(defn- gen-dotstring [filename files]
  (let [dotstring (dot/gen files)]
    (save-string-to-file filename dotstring)))

(defn- gen-lacinia [filename files]
  (save-map-to-file filename (lacinia/gen files)))

(defn- gen-graphql [filename files]
  (save-string-to-file filename (graphql/gen files)))

(defn- gen-all []
  (gen-umlaut (str base "umlaut.fixture") umlaut-files)
  (gen-dotstring (str base "dot.fixture") umlaut-files)
  (gen-lacinia (str base "lacinia.fixture") umlaut-files)
  (gen-graphql (str base "graphql.fixture") umlaut-files))

(gen-all)
