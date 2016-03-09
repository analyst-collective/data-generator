(ns data-generator.generator)


(defn run-fns
  ([fn-coll model iteration]
   (run-fns fn-coll model iteration {}))
  ([fn-coll model iteration this]
   (if (empty? fn-coll)
     this
     (let [function (first fn-coll)
           new-this (function this model :count iteration)
           new-fn-coll (rest fn-coll)]
       (recur new-fn-coll model iteration new-this)))))

(defn generate-model*
  [fn-list iterations]
  (let [iteration (first iterations)]
    (when iteration
      (println (run-fns fn-list {} iteration)) ;; Insert into db here
      (recur fn-list (rest iterations)))))

(defn generate-model
  [fn-list data]
  (let [master (reduce-kv (fn [m field fdata]
                            (if (:master fdata)
                              (merge m (:master fdata))
                              m))
                          {}
                          (:model data))
        iterations (range (:count master))]
    (generate-model* fn-list iterations)))
