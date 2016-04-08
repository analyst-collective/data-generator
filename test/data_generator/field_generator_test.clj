(ns data-generator.field-generator-test
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.test :refer :all]
            [data-generator.field-generator :refer :all]))

(deftest field-data*-uuid-test
  (testing "all"
    (let [function (field-data* {:type "uuid" :ignore "this"} :text)
          result (function :key {} {} :some :other :values)
          result-val (-> result :this :key)]
      (is (instance? clojure.lang.IPersistentMap result))
      (is (instance? java.lang.String result-val))
      (is (> (count result-val) 0)))))

(deftest field-data*-enum-test
  (testing "text"
    (let [function (field-data* {:type "enum"
                                      :weights {:$$self.field_a "$model_b.field_b"
                                                :static 1
                                                :$$null 1}}
                                     :text)
          results (map (fn [_] (function :key {:field_a "found this"
                                               :field_b "not this"} {:model_b {:field_a "not this"
                                                                               :field_b 1}}))
                       (range 100))
          result-vals (set (map #(-> % :this :key) results))]
      (is (instance? clojure.lang.PersistentHashSet result-vals))
      (is (> (count result-vals) 0))
      (is (= (count (clojure.set/difference result-vals #{"found this"
                                                          "static"
                                                          nil})) 0))))
  (testing "integer"
    (let [function (field-data* {:type "enum"
                                      :weights {:$$self.field_a "$model_b.field_b"
                                                :10 1
                                                :$$null 1}}
                                     :integer)
          results (map (fn [_] (function :key {:field_a 3
                                               :field_b "not this"} {:model_b {:field_a "not this"
                                                                               :field_b 1}}))
                       (range 100))
          result-vals (set (map #(-> % :this :key) results))]
      (is (instance? clojure.lang.PersistentHashSet result-vals))
      (is (= (count (clojure.set/difference result-vals #{3 10 nil})) 0))))
  (testing "double"
    (let [function (field-data* {:type "enum"
                                      :weights {:$$self.field_a "$model_b.field_b"
                                                :10.324 1
                                                :$$null 1}}
                                     :double-precision)
          results (map (fn [_] (function :key {:field_a 3.324
                                               :field_b "not this"} {:model_b {:field_a "not this"
                                                                               :field_b 1}}))
                       (range 100))
          result-vals (set (map #(-> % :this :key) results))]
      (is (instance? clojure.lang.PersistentHashSet result-vals))
      (is (= (count (clojure.set/difference result-vals #{3.324 10.324 nil})) 0))))
  (testing "boolean"
    (let [function (field-data* {:type "enum"
                                 :weights {:$$self.field_a "$model_b.field_b"
                                           :true 1
                                           :$$null 1}}
                                :boolean)
          results (map (fn [_] (function :key {:field_a false
                                               :field_b "not this"} {:model_b {:field_a "not this"
                                                                               :field_b 1}}))
                       (range 100))
          result-vals (set (map #(-> % :this :key) results))]
      (is (instance? clojure.lang.PersistentHashSet result-vals))
      (is (= (count (clojure.set/difference result-vals #{true false nil})) 0)))))

(deftest field-data*-concat
  (testing "static values"
    (let [function (field-data* {:type "concat"
                                 :properties {:values ["account_" 1 "_name"]}}
                                :text)
          results (function :key {} {})
          result-val (-> results :this :key)]
      (is (= result-val "account_1_name"))))
  (testing "resolved values"
    (let [function (field-data* {:type "concat"
                                 :properties {:values ["$$self.first_name" " " "$model_a.last_name"]}}
                                :text)
          results (function :key {:first_name "Mickey"} {:model_a {:last_name "Mouse"}})
          result-val (-> results :this :key)]
      (is (= result-val "Mickey Mouse")))))

(deftest field-data*-autoincrement
  (let [function (field-data* {:type "autoincrement"} :serial)
        results (function :key {:a_field 2} {})
        result-map (:this results)]
    (is (= {:a_field 2} result-map))))

(deftest field-data*-range
  (testing "bigint"
    (let [function (field-data* {:type "range"
                                 :properties {:min "$$self.count"
                                              :max 10000000000000000000}}
                                :bigint)
          results (function :key {:count 0} {})
          result-val (-> results :this :key)]
      (is (instance? clojure.lang.BigInt result-val))
      (is (and (>= result-val 0) (< result-val 10000000000000000000)))))
  (testing "integer"
    (let [function (field-data* {:type "range"
                                 :properties {:min "$$self.count"
                                              :max 10}}
                                :integer)
          results (function :key {:count 0} {})
          result-val (-> results :this :key)]
      (is (instance? java.lang.Long result-val))
      ;; Because clojure, http://stackoverflow.com/questions/9457537/why-does-int-10-produce-a-long-instance
      (is (and (>= result-val 0) (< result-val 10)))))
  (testing "float"
    (let [function (field-data* {:type "range"
                                 :properties {:min "$$self.count"
                                              :max 10.5}}
                                :real)
          results (function :key {:count 0} {})
          result-val (-> results :this :key)]
      (is (instance? java.lang.Double result-val))
      ;; Because clojure, same goes for float vs double
      ;; http://stackoverflow.com/questions/9457537/why-does-int-10-produce-a-long-instance
      (is (and (>= result-val 0) (< result-val 10.5)))))
  (testing "double"
    (let [function (field-data* {:type "range"
                                 :properties {:min "$$self.count"
                                              :max 10.5}}
                                :double-precision)
          results (function :key {:count 0} {})
          result-val (-> results :this :key)]
      (is (instance? java.lang.Double result-val))
      ;; Because clojure, same goes for float vs double
      ;; http://stackoverflow.com/questions/9457537/why-does-int-10-produce-a-long-instance
      (is (and (>= result-val 0) (< result-val 10.5)))))
  (testing "timestamp"
    (let [function (field-data* {:type "range"
                                 :properties {:min "$$self.count"
                                              :max 10}}
                                :timestamp-with-time-zone)
          results (function :key {:count 0} {})
          result-val (-> results :this :key)]
      (is (instance? java.lang.Long result-val)) ; Internally we work with longs for formula field purposes
      (is (and (>= result-val 0) (< result-val 10))))))

(deftest field-data*-faker
  (let [function (field-data* {:type "faker"
                               :function "internet.email"
                               :args ["$$self.name"]}
                              :text)
        results (function :key {:name "Mickey Mouse"} {})
        result-val (-> results :this :key)]
    (is (instance? java.lang.String result-val))
    (is (re-find #"(?i)mickey" result-val))
    (is (nil? (re-find #"(?i)\$\$self" result-val)))))

(deftest coerce-test
  (testing "nil-to-anything"
    (is (nil? (coerce nil :serial)))
    (is (nil? (coerce nil :integer)))
    (is (nil? (coerce nil :text)))
    (is (nil? (coerce nil :real)))
    (is (nil? (coerce nil :double-precision)))
    (is (nil? (coerce nil :bigserial)))
    (is (nil? (coerce nil :bigint)))
    (is (nil? (coerce nil :date)))
    (is (nil? (coerce nil :timestamp-with-time-zone)))
    (is (nil? (coerce nil :boolean))))
  (testing "integer"
    (is (instance? java.lang.Integer (coerce 1 :integer)))
    (is (= 1 (coerce 1 :integer)))
    (is (instance? java.lang.Integer (coerce "1" :integer)))
    (is (= 1 (coerce "1" :integer)))
    (is (instance? java.lang.Integer (coerce :1 :integer)))
    (is (= 1 (coerce :1 :integer))))
  (testing "text"
    (is (instance? java.lang.String (coerce "1" :text)))
    (is (= "1" (coerce "1" :text)))
    (is (instance? java.lang.String (coerce :1 :text)))
    (is (= "1" (coerce :1 :text))))
  (testing "double"
    (is (instance? java.lang.Double (coerce 1.1 :double-precision)))
    (is (= 1.1 (coerce 1.1 :double-precision)))
    (is (instance? java.lang.Double (coerce "1.1" :double-precision)))
    (is (= 1.1 (coerce "1.1" :double-precision)))
    (is (instance? java.lang.Double (coerce :1.1 :double-precision)))
    (is (= 1.1 (coerce :1.1 :double-precision))))
  (testing "real"
    (is (instance? java.lang.Float (coerce 1.1 :real)))
    (is (= (float 1.1) (coerce 1.1 :real)))
    (is (instance? java.lang.Float (coerce "1.1" :real)))
    (is (= (float 1.1) (coerce "1.1" :real)))
    (is (instance? java.lang.Float (coerce :1.1 :real)))
    (is (= (float 1.1) (coerce :1.1 :real))))
  (testing "boolean"
    (is (instance? java.lang.Boolean (coerce true :boolean)))
    (is (= true (coerce true :boolean)))
    (is (instance? java.lang.Boolean (coerce "true" :boolean)))
    (is (= true (coerce "true" :boolean)))
    (is (instance? java.lang.Boolean (coerce :true :boolean)))
    (is (= true (coerce :true :boolean))))
  (testing "datetime"
    (is (instance? org.joda.time.DateTime (coerce (t/date-time 2016 3 20) :timestamp-with-time-zone)))
    (is (= (t/date-time 2016 3 20) (coerce (t/date-time 2016 3 20) :timestamp-with-time-zone)))
    (is (instance? org.joda.time.DateTime (coerce 1458432000000 :timestamp-with-time-zone)))
    (is (= (t/date-time 2016 3 20) (coerce 1458432000000 :timestamp-with-time-zone)))
    (is (instance? org.joda.time.DateTime (coerce "1458432000000" :timestamp-with-time-zone)))
    (is (= (t/date-time 2016 3 20) (coerce "1458432000000" :timestamp-with-time-zone)))
    (is (instance? org.joda.time.DateTime (coerce :1458432000000 :timestamp-with-time-zone)))
    (is (= (t/date-time 2016 3 20) (coerce :1458432000000 :timestamp-with-time-zone)))
    (is (instance? org.joda.time.DateTime (coerce "2016-03-20" :timestamp-with-time-zone)))
    (is (= (t/date-time 2016 3 20) (coerce "2016-03-20" :timestamp-with-time-zone)))))

(deftest coerce-sql-test
  (testing "not-time"
    (is (instance? java.lang.Boolean (coerce-sql :true :boolean)))
    (is (instance? java.lang.Double (coerce-sql :2.342 :double-precision))))
  (testing "time"
    (is (instance? java.sql.Timestamp (coerce-sql "1458432000000" :timestamp-with-time-zone)))
    (is (= (c/to-sql-time (t/date-time 2016 3 20)) (coerce-sql "1458432000000" :timestamp-with-time-zone)))
    (is (instance? java.sql.Timestamp (coerce-sql :1458432000000 :timestamp-with-time-zone)))
    (is (= (c/to-sql-time (t/date-time 2016 3 20)) (coerce-sql :1458432000000 :timestamp-with-time-zone)))))
