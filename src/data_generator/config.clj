(ns data-generator.config
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.string :as s]))



(defn load-json
  [filename]
  (-> filename io/resource slurp (json/parse-string true)))

;; TODO other file format loading (yaml, edn, etc.)

(defn normalize
  ([ftype]
   (normalize ftype false))
  ([ftype autoincrement?]
   ;; (println ftype)
   (cond
     (#{"int" "integer"} ftype) (if autoincrement?
                                  :serial
                                  :integer)
     (#{"string" "text"} ftype) :text
     (re-find  #"^(var)(char)?([\s]*)?(\(([\d]*)\))?$" ftype) :text
     (#{"real" "float"} ftype) :real
     (#{"double"} ftype) :double
     (#{"bigint" "biginteger"} ftype) (if autoincrement?
                                        :bigserial
                                        :biginteger)
     (#{"date"} ftype) :date
     (#{"datetime" "timestamp" "timestamp with timezone"} ftype) :timestamp-with-time-zone
     (#{"bool" "boolean"} ftype) :boolean
                                        ; Throw error?
     :default "UNKNOWN")))

(defn normalize-fields
  [[table data]]
  (let [model (:model data)]
    [table {:model (apply
                    hash-map
                    (mapcat (fn [[field fdata]]
                              (let [ftype (-> fdata :type s/lower-case)
                                    value (:value fdata)
                                    vtype (-> fdata :value :type)
                                    autoincrement? (when vtype
                                                     (-> vtype s/lower-case (= "autoincrement")))
                                    type-norm (or (:type-norm fdata) (normalize ftype autoincrement?))]
                                [field (assoc fdata :type-norm type-norm)]))
                            model))}]))


(defn normalize-models
  [config]
  (let [models (:models config)
        fixed (apply hash-map (mapcat normalize-fields models))]
    (assoc config :models fixed)))


(defn association-field-transfer
  [config]
  (let [models (:models config)
        new-models (reduce-kv (fn check-models [m table data]
                                (let [model (:model data)
                                      new-model (reduce-kv (fn check-fields [m1 field fdata]
                                                   (if-not (= "association" (:type fdata))
                                                     (assoc m1 field fdata)
                                                     (let [data-map (or (:master fdata) (:value fdata))
                                                           associated (-> data-map :model keyword)
                                                           ;; _ (println associated fdata)
                                                           mdata (-> models associated :model)
                                                           ;; _ (println mdata)
                                                           pk-info (some (fn [[k v]]
                                                                  (and (:primarykey v)
                                                                       ;; k
                                                                       {:field k
                                                                        :type-norm (normalize (:type v))}))
                                                                mdata)
                                                           updated-m1 (-> m1
                                                                             (assoc-in [field :value :field]
                                                                                       (:field pk-info))
                                                                             (assoc-in [field :type-norm]
                                                                                    (:type-norm pk-info)))]
                                                       updated-m1)))
                                                 model
                                                 model)]
                                  (assoc-in m [table :model] new-model)))
                              models
                              models)]
    (assoc config :models new-models)))
