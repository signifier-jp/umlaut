(ns umlaut.lacinia-test
  (:require [clojure.test :refer :all]
            [umlaut.core :as core]
            [umlaut.generators.lacinia :as lacinia]
            [clojure.data :as data]))
(use '[clojure.pprint :only [pprint]])

(def fixture (read-string (slurp "test/fixtures/person/lacinia.fixture")))

(deftest lacinia-test
  (testing "Lacinia generator test"
    (let [diff (data/diff fixture
                          (lacinia/gen ["test/fixtures/person/person.umlaut" "test/fixtures/person/profession.umlaut"]))]
      (is (and
           (nil? (first diff))
           (nil? (second diff)))))))

(run-all-tests)
