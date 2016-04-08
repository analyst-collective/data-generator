(ns data-generator.storage-test
  (:require [clojure.test :refer :all]
            [data-generator.storage :refer :all]))

(deftest filter->where-criteria-test
  (testing "list"
    (is (= '(+ 1 2) (filter->where-criteria '(1 + 2)))))
  (testing "vector"
    (is (= '(+ 1 2) (filter->where-criteria '[1 + 2])))))
