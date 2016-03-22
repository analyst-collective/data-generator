(ns data-generator.dependency-resolution-test
  (:require [clojure.test :refer :all]
            [data-generator.dependency-resolution :refer :all]))


(deftest add-refs-test
  (testing "no refs"
    (is (= #{} (add-refs #{} "no refs here. 2 + 3 = 4"))))
  (testing "keep refs, add none"
    (is (= #{:field} (add-refs #{:field} "no refs here either, as;lfja;slkd23asfn"))))
  (testing "just $$self"
    (is (= #{:found} (add-refs #{} " a b $$self.found = sdfasd #asdfsd dfn")))
    (is (= #{:found} (add-refs #{} "$$self.found")))
    (is (= #{:found} (add-refs #{} "2 * ($$self.found + 3)")))
    (is (= #{:found} (add-refs #{} "2 * (3 + $$self.found)"))))
  (testing "just $a_model.a_field"
    (is (= #{'(:a_model :a_field)} (add-refs #{} "asdfk asdlkadf d $a_model.a_field")))
    (is (= #{'(:a_model :a_field)} (add-refs #{} "$a_model.a_field")))
    (is (= #{'(:a_model :a_field)} (add-refs #{} "2 * ($a_model.a_field + 3)")))
    (is (= #{'(:a_model :a_field)} (add-refs #{} "2 * (3 + $a_model.a_field)")))))

(deftest ref-locate-test
  (testing "neither"
    (is (= #{} (ref-locate #{} :test-key "nothing to see here"))))
  (testing "key"
    (is (= #{:found} (ref-locate #{} :$$self.found "nothing to see here")))
    (is (= #{'(:a_model :a_field)} (ref-locate #{} :$a_model.a_field "nothing to see here"))))
  (testing "value"
    (is (= #{:found} (ref-locate #{} :nothing-to-see-here "$$self.found"))))
  (testing "recursion"
    (is (= #{:found} (ref-locate #{} :nothing-to-see-here {:$$self.found "nothing to see here"})))
    (is (= #{:found} (ref-locate #{} :nothing-to-see-here {:nothing-to-see-here "$$self.found"})))
    (is (= #{:found} (ref-locate #{} :nothing-to-see-here [:nothing-to-see-here "$$self.found"])))
    (is (= #{:found_a :found_b :found_c :found_d} (ref-locate #{}
                                                              :$$self.found_a
                                                              {:$$self.found_b ["$$self.found_c"
                                                                                "nothing"
                                                                                "$$self.found_d"]})))))


(deftest intra-deps-test
  (testing "all"
    (is (= {:end #{:start
                   :middle
                   '(:other_model :field)}} (intra-deps {}
                                                        :end
                                                        {:type "integer"
                                                         :type-norm :integer
                                                         :value {:type "formula"
                                                                 :properties [{:equation "2 + $$self.start"}
                                                                              {:equation "$other_model.field + $$self.middle"
                                                                               :randomness 0.4}]}})))))
