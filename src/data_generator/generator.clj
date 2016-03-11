(ns data-generator.generator
  (:require [sqlingvo.core :as sql]
            [sqlingvo.db :refer [postgresql sqlite]]
            [clojure.java.jdbc :as j]
            [clojure.core.async :as a :refer [<!! <! >!! >! chan]]))

(def pg (postgresql))
(def lite (sqlite))

(defn insert
  [config table model out-chan]
  (let [db-spec (:database config)
        row (first (j/insert! db-spec table model))]
    (println "ROW" row)
    (when out-chan
      (>!! out-chan row))))

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
  [config fn-list table iterations]
  (let [iteration (first iterations)]
    (when iteration
      ;; (println (run-fns fn-list {} iteration)) ;; Insert into db here
      (let [item (run-fns fn-list {} iteration)]
        (insert config table item)
        (recur config fn-list table (rest iterations))))))

(defn generate-model
  [config fn-list table data]
  (let [master (reduce-kv (fn [m field fdata]
                            (if (:master fdata)
                              (merge m (:master fdata))
                              m))
                          {}
                          (:model data))
        iterations (range (:count master))]
    (generate-model* config fn-list table iterations)))

