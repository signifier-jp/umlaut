(ns umlaut.utils-test
  (:require [clojure.test :refer :all]
            [umlaut.utils :refer :all]))

(deftest primitive-test
  (testing "Primitive predicate"
    (is (primitive? "string"))
    (is (primitive? "String"))
    (is (primitive? "Datetime"))
    (is (primitive? "dateTime"))
    (is (primitive? "Float"))
    (is (primitive? "inTeGer"))
    (is (not (primitive? "Double")))
    (is (primitive? "Boolean"))
    (is (not (primitive? "B00l")))
    (is (not-primitive? "B00l"))))


(run-all-tests)