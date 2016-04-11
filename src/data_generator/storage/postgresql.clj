(ns data-generator.storage.postgresql
  (:require [data-generator.storage :refer [storage-prep]]
            [data-generator.storage.sql :refer [drop-col-statement]]
            [hikari-cp.core :as conn-pool]))

(def pg-namespaced (keyword "data-generator.storage" "postgresql"))

(defmethod drop-col-statement pg-namespaced
  [config table-name col-name]
  (str "ALTER TABLE \"" (name table-name) "\" DROP COLUMN \"" (name col-name) "\""))

(defn add-pool
  [config]
  (let [database (get-in config [:storage :spec])
        datasource-config (assoc {}
                                 :username (:user database)
                                 :password (:password database)
                                 :adapter (:dbtype database)
                                 :port-number (:port database)
                                 :database-name (:dbname database)
                                 :server-name (:host database)
                                 :maximum-pool-size 80)
        datasource (conn-pool/make-datasource datasource-config)]
    (assoc-in config [:storage :pool] datasource)))

(defmethod storage-prep pg-namespaced
  [config]
  (add-pool config))
