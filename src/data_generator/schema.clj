(ns data-generator.schema
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as s]
            [sqlingvo.core :as sql]
            [sqlingvo.db :refer [postgresql sqlite]]))

(def pg (postgresql))
(def lite (sqlite))

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
        create-format
        (run-commands db-spec))))

(defn drop-col-statement
  [table column]
  (str "ALTER TABLE \"" (name table) "\" DROP COLUMN \"" (name column) "\""))

(defn drop-virtual-columns
  [config]
  (let [models (:models config)
        db-spec (:database config)
        all-statements (reduce-kv (fn [all-statements table data]
                                (let [model (:model data)
                                      statements (->> model
                                                      (filter (fn [[field fdata]]
                                                                (:virtual fdata)))
                                                      (map first)
                                                      (map (partial drop-col-statement table))
                                                      (map #(conj [] %)))]
                                  (concat all-statements statements)))
                              []
                              models)]
    (j/with-db-connection [conn db-spec]
      (doseq [statement all-statements]
        (println statement)
        (j/execute! conn statement)))))
