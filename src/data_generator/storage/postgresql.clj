(ns data-generator.storage.postgresql
  (:require [data-generator.storage.sql :refer [drop-col-statement]]))

(def pg-namespaced (keyword "data-generator.storage" "postgresql"))

(defmethod drop-col-statement pg-namespaced
  [config table-name col-name]
  (str "ALTER TABLE \"" (name table-name) "\" DROP COLUMN \"" (name col-name) "\""))


