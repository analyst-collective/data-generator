(ns data-generator.generator-factory-test
  (:require [clojure.test :refer :all]
            [data-generator.generator-factory :refer :all]))


(deftest add-cumulative-tag-test
  (testing "all"
    (is (= :field_cumulative (add-cumulative-tag :field)))
    (is (= :field_cumulative_cumulative (add-cumulative-tag :field_cumulative)))))

(deftest filter->where-criteria-test
  (testing "list"
    (is (= '(+ 1 2) (filter->where-criteria '(1 + 2)))))
  (testing "vector"
    (is (= '(+ 1 2) (filter->where-criteria '[1 + 2])))))

(deftest query-filtered-test
  (testing "all"
    (is (= ["SELECT *, \"random\"() AS \"random_col_for_sorting\" FROM \"model_a\" WHERE (\"created\" < 10) ORDER BY \"random_col_for_sorting\" LIMIT 1"]
           (query-filtered :model_a '(:created < 10))))))

(deftest query-weighted-test
  (testing "all"
    (is (not (nil? (re-find #"WITH \"temp\" AS \(SELECT \*, \(\"sum\"\(\"popularity\"\) OVER \(ORDER BY \"b_pk\"\) / CAST\(\(SELECT \"sum\"\(\"popularity\"\) FROM \"model_b\"\) AS float\)\) AS \"popularity_cumulative\" FROM \"model_b\"\) SELECT \* FROM \"temp\" WHERE \(\"popularity_cumulative\" > [\d]+\.[\d]+\) ORDER BY \"popularity_cumulative\" LIMIT 1"
                            (first (query-weighted :model_b :popularity :b_pk))))))))

(deftest query-filtered-weighted-test
  (testing "all"
    (is (not (nil? (re-find #"WITH \"temp\" AS \(SELECT \*, \(\"sum\"\(\"popularity\"\) OVER \(ORDER BY \"c_pk\"\) / CAST\(\(SELECT \"sum\"\(\"popularity\"\) FROM \"model_c\" WHERE \(\"created\" > 10\)\) AS float\)\) AS \"popularity_cumulative\" FROM \"model_c\" WHERE \(\"created\" > 10\)\) SELECT \* FROM \"temp\" WHERE \(\"popularity_cumulative\" > [\d]+\.[\d]+\) ORDER BY \"popularity_cumulative\" LIMIT 1"
                            (first (query-filtered-weighted :model_c '(:created > 10) :popularity :c_pk))))))))

