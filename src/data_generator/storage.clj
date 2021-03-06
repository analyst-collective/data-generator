(ns data-generator.storage)

(derive ::postgresql ::sql)

(defn normalize-storage-type
  [config]
  (update-in config [:storage :type] #(keyword "data-generator.storage" %)))

(defn filter->where-criteria
  [[a op b]]
  (list op a b))

(defn- type-check
  [config & _]
  (get-in config [:storage :type]))

(defmulti storage-prep
  "Hook for any code that must be run prior to using storage (connection pool setup, etc.)"
  {:arglists '([config])}
  type-check)

(defmulti query
  "Returns an item at random from the provided table-name"
  {:arglists '([config table-name])}
  type-check)

(defmulti query-weighted
  "Returns an item from the provided table-name with a weighted probability from values in the provided weighted-field"
  {:arglists '([config table-name weighted-field table-pk-field])}
  type-check)

(defmulti query-filtered
  "Returns an item at random from the provided table-name filtered with the provided filter-list expression"
  {:arglists '([config table-name filter-list])}
  type-check)

(defmulti query-filtered-weighted
  "Returns an item from the provided table-name, filtered with the provided filter-list, weighted form the values in the provided weighted-field"
  {:arglists '([config table-name filter-list weighted-field table-pk-field])}
  type-check)

(defmulti query-all
  "Returns all results from the given table-name"
  {:arglists '([config table-name])}
  type-check)

(defmulti query-all-filtered
  "Returns all results from the given filter-name filtered with the provided filter-list"
  {:arglists '([config table-name filter-list])}
  type-check)

(defmulti create-tables
  "Creates tables for models"
  {:arglists '([config])}
  type-check)

(defmulti drop-virtual-columns
  "Drops virtual columns from storage so they aren't persisted"
  {:arglists '([config])}
  type-check)

(defmulti insert
  "Executes provided insert-statement and returns the result"
  {:arglists '([config insert-statement])}
  type-check)

(defmulti raw-query
  "Executes a raw query-string on the underlying storage layer"
  {:arglists '([config query-string])}
  type-check)
