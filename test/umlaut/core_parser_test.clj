(ns umlaut.core-parser-test
  (:require [clojure.test :refer :all]
            [umlaut.core :refer :all]
            [umlaut.parser :refer :all]))
(use '[clojure.pprint :only [pprint]])

(def fixture (read-string (slurp "test/fixtures/person/umlaut.fixture")))

(deftest core-test
  (testing "Umlaut input"
    (is (= fixture (umlaut.core/main ["test/fixtures/person/person.umlaut" "test/fixtures/person/profession.umlaut"])))))

(run-all-tests)
