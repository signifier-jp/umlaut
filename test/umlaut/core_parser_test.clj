(ns umlaut.core-parser-test
  (:require [clojure.test :refer :all]
            [umlaut.core :refer :all]
            [umlaut.parser :refer :all]
            [clojure.data :as data]))
(use '[clojure.pprint :only [pprint]])

(def fixture (read-string (slurp "test/fixtures/person/umlaut.fixture")))

(deftest core-test
  (testing "Umlaut input")
  (let [diff (data/diff fixture
                        (umlaut.core/main ["test/fixtures/person/person.umlaut" "test/fixtures/person/profession.umlaut"]))]
    (is (and
         (nil? (first diff))
         (nil? (second diff))))))

(run-all-tests)
