(ns data-generator.field-modifier-test
  (:require [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clojure.test :refer :all]
            [data-generator.field-modifier :refer :all]))


(deftest wrap-modifier-nil-test
  (testing "Nil passthrough"
    (is (= 3 (get-in ((wrap-modifier {} (fn [mkey this models & more]
                                          {:this {mkey 3}
                                           :models models}))
                      :dummy-key
                      {}
                      {})
                     [:this :dummy-key])))
    (is (= "testing" (get-in ((wrap-modifier {} (fn [mkey this models & more]
                                                   {:this {mkey "testing"}
                                                    :models models}))
                               :dummy-key
                               {}
                               {})
                             [:this :dummy-key])))))

(deftest wrap-modifier-time-component-test
  (require '[clj-time.core :as t]) ;; Required to be included in run-time namespace
  (testing "hour"
    (is (= 12 (get-in ((wrap-modifier {:format "time-component"
                                       :function "hour"}
                                      (fn test-fn [mkey this models & more]
                                        {:this {mkey (c/to-long (t/date-time 2016 4 11 12 20 3))}
                                         :models models}))
                       :dummy-key
                       {}
                       {})
                      [:this :dummy-key]))))
  (testing "day"
    (is (= 11 (get-in ((wrap-modifier {:format "time-component"
                                       :function "day"}
                                      (fn test-fn [mkey this models & more]
                                        {:this {mkey (c/to-long (t/date-time 2016 4 11 12 20 3))}
                                         :models models}))
                       :dummy-key
                       {}
                       {})
                      [:this :dummy-key])))
    ;; Monday = 1, Sunday is 7
    (is (= 1 (get-in ((wrap-modifier {:format "time-component"
                                       :function "day-of-week"}
                                      (fn test-fn [mkey this models & more]
                                        {:this {mkey (c/to-long (t/date-time 2016 4 11 12 20 3))}
                                         :models models}))
                       :dummy-key
                       {}
                       {})
                      [:this :dummy-key])))) 
  (testing "month"
    (is (= 4 (get-in ((wrap-modifier {:format "time-component"
                                       :function "month"}
                                      (fn test-fn [mkey this models & more]
                                        {:this {mkey (c/to-long (t/date-time 2016 4 11 12 20 3))}
                                         :models models}))
                       :dummy-key
                       {}
                       {})
                      [:this :dummy-key]))))
  (testing "year"
    (is (= 2016 (get-in ((wrap-modifier {:format "time-component"
                                       :function "year"}
                                      (fn test-fn [mkey this models & more]
                                        {:this {mkey (c/to-long (t/date-time 2016 4 11 12 20 3))}
                                         :models models}))
                       :dummy-key
                       {}
                       {})
                      [:this :dummy-key])))))
