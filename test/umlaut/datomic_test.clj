(ns umlaut.datomic-test
  (:require [clojure.test :refer :all]
            [umlaut.core :as core]
            [umlaut.generators.datomic :as datomic]
            [clojure.data :as data]))
(use '[clojure.pprint :only [pprint]])

(def fixture (read-string (slurp "test/fixtures/datomic.edn")))

(deftest datomic-test
  (testing "Datomic generator test"
    (let [diff (data/diff fixture
                          (datomic/gen ["test/fixtures/datomic.umlaut"]))]
      (is (and
           (nil? (first diff))
           (nil? (second diff)))))))

(run-all-tests)
