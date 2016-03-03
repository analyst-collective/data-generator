(ns data-generator.dependency-resolution)

(defn ref-locate
 "Recursively check values for $self.<field_name> to identify which fields (on this model) this field depends on.

  agg starts as empty set #{}"
  [agg k v]
  (cond
    (instance? java.lang.String v) (let [fields (->> v
                                                    (re-seq #"(?:^|\s)\$self\.(\w+)")
                                                    (map last)
                                                    (map keyword)
                                                    set)]
                                     (if-not (seq fields)
                                       agg
                                       (clojure.set/union agg fields)))
    (instance? clojure.lang.IPersistentMap v) (reduce-kv ref-locate
                                                         agg
                                                         v)
    :default agg))

(defn intra-deps
  "Builds a map of sets for each field on this model saving which other fields on this model they depend on

  agg starts as {}"
  [agg field fdata]
  (let [field-refs (assoc agg field (reduce-kv ref-locate
                                               #{}
                                               fdata))]
    field-refs))

(defn field-deps
  "Builds a map of sets showing which tables (if any) each table depends on. 
  Determines if it's a direct source or a select based association
  
  agg starts as {:select #{} :source #{}} and builds a list of table dependencies"
  [agg field fdata]
  (if-not (= "association" (:type fdata))
    agg
    (cond
      (:master fdata) (update agg :source #(conj % (-> fdata :master :model keyword)))
      :select (update agg :select #(conj % (-> fdata :select :model keyword))))))

(defn table-deps
  "agg starts as {} and builds a list of each tables dependencies"
  [agg table data]
  (let [added-table-deps (assoc agg table {:table-dep (reduce-kv field-deps
                                                                 {:select #{} :source #{}}
                                                                 (:model data))})
        intra-table (assoc-in added-table-deps [table :field-deps] (reduce-kv intra-deps
                                                                               {}
                                                                               (:model data)))]
    intra-table))

(defn resolve
  [config]
  (reduce-kv table-deps {} (:models config)))
