(ns data-generator.generator-factory
  (:require [clj-time.coerce :as c]
            [clojure.string :as s]
            [clojure.edn :as edn]
            [data-generator.field-generator :refer [coerce-sql
                                                    construct-where
                                                    field-data
                                                    field-data*
                                                    normalize-where
                                                    prep-filter
                                                    prep-filter-seq
                                                    remove-comparitor
                                                    resolve-references
                                                    split-filter]]
            [data-generator.storage :refer [filter->where-criteria
                                            query-all
                                            query-all-filtered]]
            [taoensso.timbre :as timbre :refer [info warn error]]))

(defn master-column
  [config data]
  (let [[field fdata] (some (fn [[field fdata]]
                              (and (:master fdata)
                                   [field fdata]))
                            data)]
    (when field
      {:key field :fn (field-data config fdata)})))

(defn independant-column
  [config data deps table]
  (let [independent-field (some (fn [[field fdeps]]
                                  (and (empty? fdeps) field))
                                (-> deps table :field-deps))]
    (when independent-field
      {:key independent-field :fn (field-data config (independent-field data))})))


(defn remove-field-dep
  [deps table remove-field]
  (let [table-field-deps (-> deps table :field-deps)
        new-tf-deps (reduce-kv (fn [m field fdeps]
                                 (assoc m field (disj fdeps remove-field)))
                               table-field-deps
                               table-field-deps)
        remove-remove-field (dissoc new-tf-deps remove-field)]
    (assoc-in deps [table :field-deps] remove-remove-field)))

(defn build-model-generator
  ([config table data deps]
   (build-model-generator config table (:model data) deps []))
  ([config table mdata deps fn-list]
   (if (empty? mdata)
     fn-list
     (let [new-item (or (master-column config mdata)
                        (independant-column config mdata deps table)
                        (do (println "REMAINING DEPS" deps)
                            (throw (Exception. (str "Circular dependency!" table)))))
           new-mdata (dissoc mdata (:key new-item))
           new-deps (remove-field-dep deps table (:key new-item))
           new-fn-list (conj fn-list new-item)]
       (recur config table new-mdata new-deps new-fn-list)))))

(defn foreach-fn-generator
  [foreach]
  (let [table (-> foreach :model keyword)
        filter-criteria (:filter foreach)
        filter-split (split-filter filter-criteria)
        filter-prepped (prep-filter filter-split table)
        filter-fields (filter keyword? (flatten filter-prepped))]
    (if-not filter-criteria
      (fn foreach-all-fn
        [mkey this models & more]
        (let [other (apply hash-map more)
              config (:config other)]
          (query-all config table)))
      (fn foreach-filter-fn
        [mkey this models & more]
        (let [other (apply hash-map more)
              config (:config other)
              filter-types (map #(-> other :config :models table :model % :type-norm) filter-fields)
              type-map (zipmap filter-fields filter-types)
              resolved-where (clojure.walk/prewalk #(resolve-references % this models) filter-prepped)
              normalized-where (normalize-where resolved-where type-map)
              constructed-where (construct-where normalized-where)]
          (query-all-filtered config table constructed-where))))))

(defn association-data
  "Adds quantifier function and likelyhood funciton"
  [data]
  (reduce-kv (fn [agg field fdata]
               (let [field-type (:type fdata)
                     master-association? (and (or (= "association" field-type)
                                                  (get-in fdata [:master :model]))
                                              (:master fdata))]
                 (if-not master-association?
                   agg
                   (let [foreach (-> fdata :master :foreach)
                         foreach-arr (when foreach
                                       (if (instance? clojure.lang.IPersistentMap foreach)
                                         [foreach]
                                         foreach))
                         foreach-keys (map #(-> % :model keyword) foreach-arr)
                         foreach-fns (map foreach-fn-generator foreach-arr)
                         quantity (-> fdata :master :quantity)
                         quantity-fn (if quantity
                                       (field-data* quantity :integer)
                                       (fn [mkey this model & more]
                                         {:this (assoc this mkey 1)
                                          :model model}))
                         probability (or (-> fdata :master :probability)
                                         1)
                         probability-fn (fn [mkey this model & more]
                                          {:this (assoc this mkey (< (rand) probability))
                                           :models model})
                         with-fns (assoc agg
                                         :quantity-fn    quantity-fn
                                         :probability-fn probability-fn
                                         :foreach-keys   foreach-keys
                                         :foreach-fns    foreach-fns)]
                     with-fns))))
             data
             (:model data)))

(defn generators
  [{:keys [models] :as config} dependencies]
  (let [new-models (reduce-kv (fn [m table data]
                                (let [fn-list (build-model-generator config table data dependencies)
                                      added-association-data (association-data data)
                                      new-data (assoc added-association-data :fn-list fn-list)]
                                  (assoc m table new-data)))
                              models
                              models)]
    (assoc config
           :models new-models)))


