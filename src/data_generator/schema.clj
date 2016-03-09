(ns data-generator.schema
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as s]
            [sqlingvo.core :as sql]
            [sqlingvo.db :refer [postgresql]]))

(def pg (postgresql))

(defn add-pk
  [col-spec fdata]
  (if (:primarykey fdata)
    (conj col-spec :primary-key? true)
    col-spec))

(defn create-format
  [models]
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
                   models)))

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
        db-spec (:database config)]
    (-> models
        ;; filter-virtual  ; After data generation, drop virtual columns!
        create-format
        (run-commands db-spec))))

