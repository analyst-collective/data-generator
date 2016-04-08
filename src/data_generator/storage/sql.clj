x(ns data-generator.storage.sql
  (:require [clojure.java.jdbc :as j]
            [data-generator.storage :refer [filter->where-criteria
                                            query
                                            query-weighted
                                            query-filtered
                                            query-filtered-weighted
                                            query-all
                                            query-all-filtered
                                            create-tables
                                            drop-virtual-columns
                                            insert
                                            raw-query]]
            [sqlingvo.core :as sql]
            [sqlingvo.db :refer [postgresql]]))

(def db {(keyword "data-generator.storage" "postgresql") (postgresql)})

(def sql-namespaced (keyword "data-generator.storage" "sql"))

(defn add-cumulative-tag
  [field]
  (-> field name (str "_cumulative") keyword))

(defn execute-query
  [config query]
  (let [pool (get-in config [:storage :pool])]
    (j/with-db-connection [conn {:datasource pool}]
      (j/query conn query))))

(defn query-statement
  [config table-name]
  (sql/sql
   (sql/select
    ((get-in config [:storage :type]) db)
    [:* (sql/as '(random) :random_col_for_sorting)]
    (sql/from table-name)
    (sql/order-by :random_col_for_sorting)
    (sql/limit 1))))

(defmethod query sql-namespaced
  [config table-name]
  (let [statement (query-statement config table-name)]
    (execute-query config statement)))

(defn query-weighted-statement
  [config table-name weighted-field table-pk-field]
  (let [cumulative (add-cumulative-tag weighted-field)]
    (sql/sql
     (sql/with
      ((get-in config [:storage :type]) db)
      [:temp (sql/select
              ((get-in config [:storage :type]) db)
              [:* (sql/as
                   `(/ ((over
                         (sum ~weighted-field)
                         (order-by ~table-pk-field)))
                       (cast ~(sql/select
                               ((get-in config [:storage :type]) db)
                               [`(sum ~weighted-field)]
                               (sql/from table-name)) :float))
                   cumulative)] (sql/from table-name))]
               (sql/select
                ((get-in config [:storage :type]) db)
                [:*]
                (sql/from :temp)
                (sql/where `(> ~cumulative ~(rand)))
                (sql/order-by cumulative)
                (sql/limit 1))))))

(defmethod query-weighted sql-namespaced
  [config table-name weighted-field table-pk-field]
  (let [statement (query-weighted-statement config table-name weighted-field table-pk-field)]
    (execute-query config statement)))

(defn query-filtered-statement
  [config table-name filter-list]
  (sql/sql
   (sql/select
       ((get-in config [:storage :type]) db)
       [:* (sql/as '(random) :random_col_for_sorting)]
       (sql/from table-name)
                                        ; TODO support OR/AND criteria
       (sql/where (filter->where-criteria filter-list))
       (sql/order-by :random_col_for_sorting)
       (sql/limit 1))))

(defmethod query-filtered sql-namespaced
  [config table-name filter-list]
  (let [statement (query-filtered-statement)]
    (execute-query config statement)))

(defn query-filtered-weighted-statement
  [config table-name filter-list weighted-field table-pk-field]
  (let [cumulative (add-cumulative-tag weighted-field)
        where-statement (filter->where-criteria filter-list)]
    (sql/sql
     (sql/with
      ((get-in config [:storage :type]) db)
      [:temp (sql/select
              ((get-in config [:storage :type]) db)
              [:* (sql/as
                   `(/ ((over
                         (sum ~weighted-field)
                         (order-by ~table-pk-field)))
                       (cast ~(sql/select ((get-in config [:storage :type]) db) [`(sum ~weighted-field)]
                                          (sql/from table-name)
                                          (sql/where where-statement)) :float))
                   cumulative)]
              (sql/from table-name)
              (sql/where where-statement))]
       (sql/select
           ((get-in config [:storage :type]) db)
           [:*]
         (sql/from :temp)
         (sql/where `(> ~cumulative ~(rand)))
         (sql/order-by cumulative)
         (sql/limit 1))))))

(defmethod query-filtered-weighted sql-namespaced
  [config table-name filter-list weighted-field table-pk-field]
  (let [statement (query-filtered-weighted-statement config table-name filter-list weighted-field table-pk-field)]
    (execute-query config statement)))

(defn query-all-statement
  [config table-name]
  (sql/sql
   (sql/select
    ((get-in config [:storage :type]) db)
    [:*]
    (sql/from table-name))))

(defmethod query-all sql-namespaced
  [config table-name]
  (sql/sql
   (sql/select
    ((get-in config [:storage :type]) db)
    [:*]
    (sql/from table-name))))

(defn query-all-filtered-statement
  [config table-name filter-list]
  (sql/sql
   (sql/select
    ((get-in config [:storage :type]) db)
    [:*]
    (sql/from table-name) (sql/where filter-list))))

(defmethod query-all-filtered sql-namespaced
  [config table-name filter-list]
  (let [statement (query-all-filtered-statement config table-name filter-list)]
    (execute-query config statement)))

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
  [config formatted]
  (j/with-db-connection [conn (get-in config [:storage :spec])]
    (doseq [[table cols] formatted]
      (let [drop-statement (sql/sql (sql/drop-table ((get-in config [:storage :type]) db)
                                                    [table]
                                                    (sql/if-exists true)))
            col-fns (concat [((get-in config [:storage :type]) db) table]
                            (map #(apply sql/column %) cols))
            create-statement (sql/sql (apply sql/create-table col-fns))]
        (println drop-statement)
        (println create-statement)
        (j/execute! conn drop-statement)
        (j/execute! conn create-statement)))))

(defmethod create-tables sql-namespaced
  [config]
  (let [models (just-models config)]
    (->> models
         create-format
         (run-commands config))))

(defmulti drop-col-statement
  "Returns a string with a properly formatted 'DROP COLUMN' statement for the sql implementation"
  {:arglists '([config table-name col-name])}
  (fn [config _ _]
    (get-in config [:storage :type])))


(defmethod drop-virtual-columns sql-namespaced
  [config]
  (let [models (:models config)
        pool (get-in config [:storage :pool])
        all-statements (reduce-kv (fn [all-statements table data]
                                (let [model (:model data)
                                      statements (->> model
                                                      (filter (fn [[field fdata]]
                                                                (:virtual fdata)))
                                                      (map first)
                                                      (map (partial drop-col-statement config table))
                                                      (map #(conj [] %)))]
                                  (concat all-statements statements)))
                              []
                              models)]
    (j/with-db-connection [conn {:datasource pool}]
      (doseq [statement all-statements]
        (println statement)
        (j/execute! conn statement)))))

(defn insert-statement
  [config table object]
  (sql/sql (sql/insert
               ((get-in config [:storage :type]) db)
               table
               []
             (sql/values [object]))))

(defmethod insert sql-namespaced
  [config table-name object]
  (let [pool (get-in config [:storage :pool])
        statement (insert-statement config table-name object)
        prepped [(first statement) (rest statement)]]
    (try (j/with-db-connection [conn {:datasource pool}]
           (apply j/db-do-prepared-return-keys conn prepped))
         (catch Exception e (do (println "TROUBLE" prepped)
                                (throw e))))))

(defmethod raw-query sql-namespaced
  [config query-string]
  (execute-query config [query-string]))
