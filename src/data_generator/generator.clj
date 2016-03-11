(ns data-generator.generator
  (:require [sqlingvo.core :as sql]
            [sqlingvo.db :refer [postgresql sqlite]]
            [clojure.java.jdbc :as j]
            [clj-time.coerce :refer [from-long to-sql-date]]
            [incanter.distributions :as id] ;; functions are resovled in this namespace
            [incanter.core :refer [$=]] ;; macro is resolved at runtime in this namespace
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

(defn coerce-dates
  [item data]
  (println "BEFORE" item)
  (reduce-kv (fn [new-item field fdata]
               (if (#{:timestamp-with-time-zone} (:type-norm fdata))
                 (assoc new-item field (-> item field long to-sql-date) #_(-> item field long from-long))
                 new-item))
             item
             (:model data)))

(defn generate-model*
  [config fn-list table data iterations]
  (let [iteration (first iterations)]
    (when iteration
      (let [item (run-fns fn-list {} iteration)
            skip? (->> item vals (some #{:none}))] ;; Select association returned nothing
        (when-not skip?
          (insert config table (coerce-dates item data) false)
          (println "AFTER" (coerce-dates item data)))
        (recur config fn-list table data (rest iterations))))))

(defn generate-model
  [config fn-list table data]
  (let [master (reduce-kv (fn [m field fdata]
                            (if (:master fdata)
                              (merge m (:master fdata))
                              m))
                          {}
                          (:model data))
        iterations (range (:count master))]
    (generate-model* config fn-list table data iterations)))


(defn generate
  [config dependencies]
  (clojure.pprint/pprint config)
  (clojure.pprint/pprint dependencies)
  config)


(defn test-salesperson
  [config model-name]
  (generate-model config (-> config :models model-name :fn-list) model-name (-> config :models model-name)))
