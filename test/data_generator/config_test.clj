(ns data-generator.config-test
  (:require [clojure.test :refer :all]
            [data-generator.config :refer :all]))

(deftest normalize-test
  (testing "integer"
    (is (= :serial (normalize "int" true)))
    (is (= :serial (normalize "integer" true)))
    (is (= :integer (normalize "int")))
    (is (= :integer (normalize "integer"))))
  (testing "text"
    (is (= :text (normalize "string")))
    (is (= :text (normalize "text")))
    (is (= :text (normalize "var  (50)")))
    (is (= :text (normalize "varchar(10313)"))))
  (testing "real"
    (is (= :real (normalize "float")))
    (is (= :real (normalize "real"))))
  (testing "double"
    (is (= :double-precision (normalize "double")))
    (is (= :double-precision (normalize "double precision"))))
  (testing "date"
    (is (= :date (normalize "date"))))
  (testing "datetime"
    (is (= :timestamp-with-time-zone (normalize "datetime")))
    (is (= :timestamp-with-time-zone (normalize "timestamp")))
    (is (= :timestamp-with-time-zone (normalize "timestamp with time zone"))))
  (testing "boolean"
    (is (= :boolean (normalize "bool")))
    (is (= :boolean (normalize "boolean"))))
  (testing "invalid"
    (is (thrown-with-msg? Exception #"Field type baboon is invalid\." (normalize "baboon")))))


(deftest normalize-fields-test
  (testing "all valid"
    (is (= [:table_a {:model {:id {:type "INTEGER"
                                   :type-norm :serial
                                   :value {:type "autoincrement"}}
                              :name {:type "varchar (30)"
                                     :type-norm :text
                                     :value {:type "blah blah"}}
                              :assoc {:type "association"
                                      :type-norm :bigint
                                      :value {:model :assoc}}}}]
           (normalize-fields [:table_a {:model {:id {:type "INTEGER"
                                                     :value {:type "autoincrement"}}
                                                :name {:type "varchar (30)"
                                                       :value {:type "blah blah"}}
                                                :assoc {:type "association"
                                                        :type-norm :bigint
                                                        :value {:model :assoc}}}}])))))

(deftest normalize-models-test
  (testing "all valid"
    (is (= {:unimportant "asdlkfjas"
            :models {:model_a {:model {:id {:type "INTEGER"
                                            :type-norm :serial
                                            :value {:type "autoincrement"}}
                                       :name {:type "varchar (30)"
                                              :type-norm :text
                                              :value {:type "blah blah"}}
                                       :assoc {:type "association"
                                               :type-norm :bigint
                                               :value {:model :assoc}}}}
                     :model_b {:model {:id {:type "biginteger"
                                            :type-norm :bigserial
                                            :value {:type "autoincrement"}}
                                       :valid {:type "bool"
                                               :type-norm :boolean
                                               :value {:type "enum"
                                                       :weights {:true 9
                                                                 :false 1}}}}}}}
           (normalize-models {:unimportant "asdlkfjas"
                              :models {:model_a {:model {:id {:type "INTEGER"
                                                              :value {:type "autoincrement"}}
                                                         :name {:type "varchar (30)"
                                                                :value {:type "blah blah"}}
                                                         :assoc {:type "association"
                                                                 :type-norm :bigint
                                                                 :value {:model :assoc}}}}
                                       :model_b {:model {:id {:type "biginteger"
                                                              :value {:type "autoincrement"}}
                                                         :valid {:type "bool"
                                                                 :value {:type "enum"
                                                                         :weights {:true 9
                                                                                   :false 1}}}}}}})))))

(deftest association-field-transfer-test
  (testing "all valid"
    (is (= {:unimportant "asdfasdf"
            :models {:model_x {:model {:id {:type "bigint"
                                            :primarykey true
                                            :value {:type "autoincrement"}}
                                       :name {:type "text"
                                              :value {:type "blah blah"}}}}
                     :model_y {:model {:id {:type "int"
                                            :primarykey true
                                            :value {:type "autoincrement"}}
                                       :model_x {:type "association"
                                                 :type-norm :bigint
                                                 :master {:model "model_x"}
                                                 :value {:field :id}}}}
                     :model_z {:model {:id {:type "integer"
                                            :primarykey true
                                            :value {:type "autoincrement"}}
                                       :model_y {:type "association"
                                                 :type-norm :integer
                                                 :value {:model "model_y"
                                                         :field :id}}}}}}
           (association-field-transfer
            {:unimportant "asdfasdf"
            :models {:model_x {:model {:id {:type "bigint"
                                            :primarykey true
                                            :value {:type "autoincrement"}}
                                       :name {:type "text"
                                              :value {:type "blah blah"}}}}
                     :model_y {:model {:id {:type "int"
                                            :primarykey true
                                            :value {:type "autoincrement"}}
                                       :model_x {:type "association"
                                                 :master {:model "model_x"}
                                                 :value {:field :id}}}}
                     :model_z {:model {:id {:type "integer"
                                            :primarykey true
                                            :value {:type "autoincrement"}}
                                       :model_y {:type "association"
                                                 :value {:model "model_y"
                                                         :field :id}}}}}})))))
