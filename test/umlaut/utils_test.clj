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
    (is (= {:a 3} (map-extend {} {:a 3})))
    (is (= {:b 1, :a 3} (map-extend {:b 1} {:a 3})))
    (is (= {:a {:b 1}, :c 4} (map-extend {:a {:b 1}} {:c 4})))
    (is (= {:a {:b 1}, :c 4} (map-extend {:a {:b 1}} {:c 4})))
    (is (= {:a {:b 2 :c 4}} (map-extend {:a {:b 1 :c 4}} {:a {:b 2}})))
    (is (= {:a {:b 2, :c 3}} (map-extend {:a {:b 1}} {:a {:b 2 :c 3}})))
    (is (= {:a {:b 2, :c 3}, :c 5} (map-extend {:a {:b 1}} {:a {:b 2 :c 3} :c 5})))
    (is (= {:a {:b {:c {:d 6}}, :h 5}} (map-extend {:a {:b {:c {:d 5}}}} {:a {:b {:c {:d 6}} :h 5}})))
    (is (in? 3 [1 2 3]))
    (is (in? 3 '(1 2 3)))
    (is (in? {} '(1 {} 3)))
    (is (in? {:a 2} '(1 {:a 2} 3)))
    (is (not (in? 4 [1 2 3])))))

(run-all-tests)
