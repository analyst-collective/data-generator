(ns data-generator.storage.sql
  (:require [clojure.java.jdbc :as j]
            [data-generator.storage :refer [execute-query
                                            query
                                            query-weighted
                                            query-filtered
                                            query-filtered-weighted
                                            query-all
                                            query-all-filtered
                                            create-tables
                                            drop-virtual-columns]]
            [sqlingvo.core :as sql]
            [sqlingvo.db :refer [postgresql]]))

(def db {:postgresql (postgresql)})

(def sql-namespaced (keyword "data-generator.storage" "sql"))

(defn add-cumulative-tag
  [field]
  (-> field name (str "_cumulative") keyword))

(defn filter->where-criteria
  [[a op b]]
  (list op a b))

(defmethod execute-query sql-namespaced
  [config query]
  (let [pool (get-in config [:storage :pool])]
    (j/with-db-connection [conn {:datasource pool}]
      (j/query conn query))))

(defmethod query sql-namespaced
  [config table-name]
  (sql/sql
   (sql/select
    ((get-in config [:storage :type]) db)
    [:* (sql/as '(random) :random_col_for_sorting)]
    (sql/from table-name)
    (sql/order-by :random_col_for_sorting)
    (sql/limit 1))))

(defmethod query-weighted sql-namespaced
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

(defmethod query-filtered sql-namespaced
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

(defmethod query-filtered-weighted sql-namespaced
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

(defmethod query-all sql-namespaced
  [config table-name]
  (sql/sql
   (sql/select
    ((get-in config [:storage :type]) db)
    [:*]
    (sql/from table-name))))

(defmethod query-all-filtered sql-namespaced
  [config table-name filter-list]
  (sql/sql
   (sql/select
    ((get-in config [:storage :type]) db)
    [:*]
    (sql/from table-name) (sql/where filter-list))))

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
  [config db-spec formatted]
  (j/with-db-connection [conn db-spec]
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
  (let [models (just-models config)
        _ (println "MODELS" (keys models))
        db-spec (:database config)]
    (->> models
         create-format
         (run-commands db-spec))))

(defmulti drop-col-statement
  "Returns a string with a properly formatted 'DROP COLUMN' statement for the sql implementation"
  {:arglists '([config table-name col-name])}
  (fn [config]
    (get-in config [:storage :type])))


(defmethod drop-virtual-columns sql-namespaced
  [config]
  (let [models (:models config)
        db-spec (:database config)
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
    (j/with-db-connection [conn db-spec]
      (doseq [statement all-statements]
        (println statement)
        (j/execute! conn statement)))))
