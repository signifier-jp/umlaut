(ns umlaut.all-test
  (:require [clojure.test :refer :all]
            [umlaut.core-parser-test :as core]
            [umlaut.dot-test :as dot]
            [umlaut.utils-test :as utils]
            [umlaut.lacinia-test :as lacinia]))

(run-tests (find-ns 'umlaut.core-parser-test)
           (find-ns 'umlaut.dot-test)
           (find-ns 'umlaut.utils-test)
           (find-ns 'umlaut.lacinia-test)
           (find-ns 'umlaut.graphql-test))
