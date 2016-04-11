(ns data-generator.field-modifier
  (:require [clj-time.coerce :as c]
            [clj-time.core :as t]
            [taoensso.timbre :as timbre :refer [info warn error]]))

(defmulti wrap-modifier (fn [modifier _]
                          (:format modifier)))

;; Pass value function through if no modifier
(defmethod wrap-modifier nil
  [_ function]
  function)

(defmethod wrap-modifier "time-component"
  [modifier function]
  (let [modifier-fn-name (:function modifier)
        modifier-fn-args (:args modifier)
        modifier-fn (resolve (symbol "t" modifier-fn-name))
        _ (when-not modifier-fn
            (throw (Exception. (str "Invalid modifier specified "
                                    modifier
                                    modifier-fn
                                    (symbol "t" modifier-fn-name)))))
        modifier-fn-wrapped #(-> %
                                long
                                c/from-long
                                modifier-fn)]
    (fn time-component-modifier [mkey this models & more]
      (let [result (apply function (list* mkey this models more))
            result-val (-> result :this mkey long c/from-long)]
        (try (apply update-in (list* result [:this mkey] modifier-fn-wrapped modifier-fn-args))
             (catch Exception e (do (error "TIME COMPONENT" (list* result [:this mkey] modifier-fn-wrapped modifier-fn-args))
                                    (throw e))))))))


