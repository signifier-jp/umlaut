(ns umlaut.utils-test
  (:require [clojure.test :refer :all]
            [umlaut.utils :refer :all]))

(deftest utils-test
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
    (is (not-primitive? "B00l"))
    (is (primitive? "ID"))
    (is (primitive? "id")))

  (testing "AST types"
    (is (diagram? [:diagram]))
    (is (interface? [:interface]))
    (is (enum? [:enum]))
    (is (type-interface-or-enum? [:type]))
    (is (type-interface-or-enum? [:interface])))

  (testing "Data manipulation"
    (is (in? 3 [1 2 3]))
    (is (in? 3 '(1 2 3)))
    (is (in? {} '(1 {} 3)))
    (is (in? {:a 2} '(1 {:a 2} 3)))
    (is (not (in? 4 [1 2 3])))))

(run-all-tests)
