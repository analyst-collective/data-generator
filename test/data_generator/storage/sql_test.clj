(ns data-generator.storage.sql-test
  (:require [clojure.test :refer :all]
            [data-generator.storage.sql :refer :all]))

(deftest add-cumulative-tag-test
  (testing "all"
    (is (= :field_cumulative (add-cumulative-tag :field)))
    (is (= :field_cumulative_cumulative (add-cumulative-tag :field_cumulative)))))
