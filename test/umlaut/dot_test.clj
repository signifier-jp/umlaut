(ns umlaut.dot-test
  (:require [clojure.test :refer :all]
            [umlaut.core :refer :all]
            [umlaut.generators.dot :as dot]))
(use '[clojure.pprint :only [pprint]])

(def fixture (slurp "test/fixtures/person/dot.fixture"))

(deftest dot-test
  (testing "Dot generator test"
    (is (= fixture (dot/gen ["test/fixtures/person/person.umlaut" "test/fixtures/person/profession.umlaut"])))))

(run-all-tests)
