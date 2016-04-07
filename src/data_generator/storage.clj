(ns data-generator.storage)

(derive ::postgresql ::sql)

(defn normalize-storage-type
  [config]
  (update-in config [:storage :type] #(keyword "data-generator.storage" %)))

(defmulti storage-prep
  "Hook for any code that must be run prior to using storage (connection pool setup, etc.)"
  {:arglists '([config])}
  (fn [config]
    (get-in config [:storage :type])))

(defmulti execute-query
  "Executes the provided query, returning the response values"
  {:arglists '([config query])}
  (fn [config _]
    (get-in config [:storage :type])))

(defmulti query
  "Returns an item at random from the provided table-name"
  {:arglists '([config table-name])}
  (fn [config _]
    (get-in config [:storage :type])))

(defmulti query-weighted
  "Returns an item from the provided table-name with a weighted probability from values in the provided weighted-field"
  {:arglists '([config table-name weighted-field table-pk-field])}
  (fn [config _ _ _]
    (get-in config [:storage :type])))

(defmulti query-filtered
  "Returns an item at random from the provided table-name filtered with the provided filter-list expression"
  {:arglists '([config table-name filter-list])}
  (fn [config _ _]
    (get-in config [:storage :type])))

(defmulti query-filtered-weighted
  "Returns an item from the provided table-name, filtered with the provided filter-list, weighted form the values in the provided weighted-field"
  {:arglists '([config table-name filter-list weighted-field table-pk-field])}
  (fn [config _ _ _ _]
    (get-in config [:storage :type])))

(defmulti query-all
  "Returns all results from the given table-name"
  {:arglists '([config table-name])}
  (fn [config _]
    (get-in config [:storage :type])))

(defmulti query-all-filtered
  "Returns all results from the given filter-name filtered with the provided filter-list"
  {:arglists '([config table-name filter-list])}
  (fn [config _ _]
    (get-in config [:storage :type])))

(defmulti create-tables
  "Creates tables for models"
  {:arglists '([config])}
  (fn [config]
    (get-in config [:storage :type])))


(defmulti drop-virtual-columns
  "Drops virtual columns from storage so they aren't persisted"
  {:arglists '([config])}
  (fn [config]
    (get-in config [:storage :type])))

