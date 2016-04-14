(ns data-generator.storage.postgresql-test
  (:require [clojure.test :refer :all]
            [data-generator.storage.sql :refer :all]))

(def pg-config {:storage {:type (keyword "data-generator.storage" "postgresql")
                          :spec {}}
                :models {}})

(deftest query-filtered-statement-test
  (testing "all"
    (is (= ["SELECT *, \"random\"() AS \"random_col_for_sorting\" FROM \"model_a\" WHERE (\"created\" < 10) ORDER BY \"random_col_for_sorting\" LIMIT 1"]
           (query-filtered-statement pg-config :model_a '(:created < 10))))))

(deftest query-weighted-statement-test
  (testing "all"
    (is (not (nil? (re-find #"WITH \"temp\" AS \(SELECT \*, \(\"sum\"\(\"popularity\"\) OVER \(ORDER BY \"b_pk\"\) / CAST\(\(SELECT \"sum\"\(\"popularity\"\) FROM \"model_b\"\) AS float\)\) AS \"popularity_cumulative\" FROM \"model_b\"\) SELECT \* FROM \"temp\" WHERE \(\"popularity_cumulative\" > [\d]+\.[\d]+\) ORDER BY \"popularity_cumulative\" LIMIT 1"
                            (first (query-weighted-statement pg-config :model_b :popularity :b_pk))))))))

(deftest query-filtered-weighted-statement-test
  (testing "all"
    (is (not (nil? (re-find #"WITH \"temp\" AS \(SELECT \*, \(\"sum\"\(\"popularity\"\) OVER \(ORDER BY \"c_pk\"\) / CAST\(\(SELECT \"sum\"\(\"popularity\"\) FROM \"model_c\" WHERE \(\"created\" > 10\)\) AS float\)\) AS \"popularity_cumulative\" FROM \"model_c\" WHERE \(\"created\" > 10\)\) SELECT \* FROM \"temp\" WHERE \(\"popularity_cumulative\" > [\d]+\.[\d]+\) ORDER BY \"popularity_cumulative\" LIMIT 1"
                            (first (query-filtered-weighted-statement pg-config :model_c '(> :created 10) :popularity :c_pk))))))))
