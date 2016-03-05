(ns data-generator.schema
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as s]
            [sqlingvo.core :as sql]
            [sqlingvo.db :refer [postgresql]]))

(def pg (postgresql))

(defn associate-types
  [models pkmap]
  (apply
   hash-map
   (mapcat
    (fn [[table data]]
      [table (apply
              sorted-map
              (mapcat
               (fn [[field fdata]]
                 (let [type (:type fdata)
                       new-fdata (if (not= type "association")
                                   [field fdata]
                                   (let [assoc-type (or (:master fdata) (:select fdata))
                                         ;; assoc-model (-> fdata :value :to keyword)
                                         assoc-model (-> assoc-type :model keyword)
                                         assoc-model-field (assoc-model pkmap keyword)
                                         assoc-model-field-type (-> models
                                                                    assoc-model
                                                                    assoc-model-field
                                                                    :type)]
                                     [field (assoc fdata :type assoc-model-field-type)]))]
                   new-fdata))
               data))])
    models)))

(defn add-pk
  [col-spec fdata]
  (if (:primarykey fdata)
    (conj col-spec :primary-key? true)
    col-spec))

;; (defn add-type
;;   [col-spec fdata]
;;   (let [type (-> fdata :type s/lower-case)
;;         value (:value fdata)
;;         autoincrement? (when value
;;                          (-> value :type s/lower-case (= "autoincrement")))
;;         normalized-type (cond
;;                           (#{"int" "integer"} type) (if autoincrement?
;;                                                       :serial
;;                                                       :integer)
;;                           (#{"string" "text"} type) :text
;;                           (re-find  #"^(var)(char)?([\s]*)?(\(([\d]*)\))?$" type) :text
;;                           (#{"real" "float"} type) :real
;;                           (#{"double"} type) :double
;;                           (#{"bigint" "biginteger"} type) (if autoincrement?
;;                                                             :bigserial
;;                                                             :biginteger)
;;                           (#{"date"} type) :date
;;                           (#{"datetime" "timestamp" "timestamp with timezone"} type) :timestamp-with-time-zone
;;                           (#{"bool" "boolean"} type) :boolean
;;                               ; Throw error?
;;                           :default "UNKNOWN")]
;;     (conj col-spec normalized-type)))

(defn create-format
  [injected-config]
  (apply sorted-map (mapcat
                   (fn [[table data]]
                     [table (map
                             (fn [[field fdata]]
                               (-> [field]
                                   ;(add-type fdata)
                                   ;; :type-norm
                                   (conj (:type-norm fdata))
                                   (add-pk fdata)))
                             data)])
                   injected-config)))

(defn filter-virtual
  [models]
  (apply sorted-map (mapcat
                   (fn [[table data]]
                     (->> data
                          (remove (fn [[_ fdata]] (:virtual fdata)))
                          (conj [table])))
                   models)))

(defn just-models
  [config]
  (apply sorted-map (mapcat
                   (fn [[table data]]
                     [table (:model data)])
                   (:models config))))

(defn extract-pk-map
  [models]
  (apply sorted-map
         (mapcat
          (fn [[table data]]
            [table (->> data
                        (map (fn [[field fdata]]
                               [field (:primarykey fdata)]))
                        (filter second)
                        ffirst)])
          models)))

(defn run-commands
  [formatted db-spec]
  (j/with-db-connection [conn db-spec]
    (doseq [[table cols] formatted]
      (let [drop-statement (sql/sql (sql/drop-table pg [table] (sql/if-exists true)))
            col-fns (concat [pg table] (map #(apply sql/column %) cols))
            create-statement (sql/sql (apply sql/create-table col-fns))]
        (println drop-statement)
        (println create-statement)
        (j/execute! conn drop-statement)
        (j/execute! conn create-statement)))))

(defn create-tables
  [config]
  (let [models (just-models config)
        pkmap (extract-pk-map models)
        db-spec (:database config)]
    (-> models
        (associate-types pkmap)
        ;; filter-virtual  ; After data generation, drop virtual columns!
        create-format
        (run-commands db-spec))))

