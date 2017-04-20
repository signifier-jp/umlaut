(ns umlaut.generators.fixtures
  (:require [clojure.java.io :as io]
            [umlaut.generators.dot :as dot]
            [umlaut.generators.lacinia :as lacinia]
            [umlaut.utils :refer :all]
            [clojure.spec :as s]
            [clojure.spec.test :as stest]))
(use '[clojure.pprint :only [pprint]])

;; IMPORTANT!
;; This fixture generator should ONLY be used if you changed
;; the umlaut code inside the fixture folder. Changes in the
;; code required a manual fixture fix.

(def ^:private base "test/fixtures/person/")
(def ^:private umlaut-files [(str base "person.umlaut") (str base "profession.umlaut")])

(defn- gen-umlaut [filename umlaut]
  (save-map-to-file filename umlaut))

(defn- gen-dotstring [filename umlaut]
  (let [dotstring (dot/gen-all umlaut)]
    (save-string-to-file filename dotstring)))

(defn- gen-lacinia [filename umlaut]
  (save-map-to-file filename (lacinia/gen umlaut)))

(defn- gen-all [umlaut]
  (gen-umlaut (str base "umlaut.fixture") umlaut)
  (gen-dotstring (str base "dot.fixture") umlaut)
  (gen-lacinia (str base "lacinia.fixture") umlaut))

(gen-all (umlaut.core/main umlaut-files))
