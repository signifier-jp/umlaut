(ns umlaut.lacinia-test
  (:require [clojure.test :refer :all]
            [umlaut.core :as core]
            [umlaut.generators.lacinia :as lacinia]))
(use '[clojure.pprint :only [pprint]])

(def fixture (read-string (slurp "test/fixtures/person/lacinia.fixture")))

(deftest lacinia-test
  (testing "Lacinia generator test"
    (is (= fixture (lacinia/gen ["test/fixtures/person/person.umlaut" "test/fixtures/person/profession.umlaut"])))))

(run-all-tests)
