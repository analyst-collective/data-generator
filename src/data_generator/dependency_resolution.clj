(ns data-generator.dependency-resolution
  (:require [clojure.core.async :as a :refer [chan mult tap]]))

(defn add-refs
  [refs v]
  (let [fields (->> v
                    (re-seq #"(?:^|\s)\$\$self\.(\w+)")
                    (map last)
                    (map keyword)
                    set)
        associations (->> v
                          (re-seq #"(?:^| )\$([\w]*)\.(\w+)")
                          (map rest)
                          (map #(map keyword %))
                          set)]
    (clojure.set/union refs fields associations)))

(defn ref-locate
 "Recursively check values for $self.<field_name> to identify which fields (on this model) this field depends on.

  agg starts as empty set #{}"
  [agg k v]
  (let [key-refs (add-refs agg (if (keyword k)
                                 (name k)
                                 (str k)))
        val-refs (cond
                   (instance? java.lang.String v) (add-refs agg v)
                   (or (instance? clojure.lang.IPersistentMap v)
                       (instance? clojure.lang.IPersistentVector v)) (reduce-kv ref-locate
                                                                                agg
                                                                                v)
                   :default agg)]
    (clojure.set/union key-refs val-refs)))

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
      :select (update agg :select #(conj % (-> fdata :value :model keyword))))))

(defn table-deps
  "agg starts as {} and builds a list of each tables dependencies"
  [agg table data]
  (let [added-table-deps (assoc agg
                                table
                                {:table-dep (reduce-kv field-deps
                                                       {:select #{} :source #{}}
                                                       (:model data))})
        intra-table (reduce-kv intra-deps {} (:model data))
        intra-table-associations (assoc-in added-table-deps
                                            [table :field-deps]
                                            (reduce-kv
                                             (fn [m field fset]
                                               (let [new-fset (reduce
                                                               (fn [depset value]
                                                                 (if (keyword? value)
                                                                   (conj depset value)
                                                                   (let [model (first value)
                                                                         assoc-field (some (fn [[mfield fdata]]
                                                                                       (or (and (= (-> fdata
                                                                                                       :master
                                                                                                       :model
                                                                                                       keyword)
                                                                                                   model)
                                                                                                mfield)
                                                                                           (and (= (-> fdata
                                                                                                       :value
                                                                                                       :model
                                                                                                       keyword)
                                                                                                   model)
                                                                                                mfield)))
                                                                                     (:model data))]
                                                                     (if assoc-field
                                                                       (conj depset assoc-field)
                                                                       depset))))
                                                               #{}
                                                               fset)]
                                                 (assoc m field new-fset)))
                                             {}
                                             intra-table))]
    intra-table-associations))

(defn chan-setup
  [dependencies]
  (let [sources (->> dependencies
                     (map second)
                     (map :table-dep)
                     (map :source)
                     (apply clojure.set/union))
        added-src (reduce-kv (fn [m table deps]
                               (if (sources table)
                                 (let [src-chan (chan 100)
                                       src-mult (mult src-chan)
                                       new-deps (assoc deps :src-pub src-chan :src-mult src-mult)]
                                   (assoc m table new-deps))
                                 m))
                             dependencies
                             dependencies)
        added-done-and-src-sub (reduce-kv (fn [m table deps]
                                            (let [added-done-chan (assoc deps :done-chan (chan))
                                                  source (-> deps :table-dep :source first)
                                                  added-src-chan (if-not source
                                                                   added-done-chan
                                                                   (let [src-sub (chan 100)
                                                                         src-mult (-> m source :src-mult)
                                                                         tapped (tap src-mult src-sub)
                                                                         with-source (assoc added-done-chan
                                                                                            :src-sub
                                                                                            src-sub)]
                                                                     with-source))]
                                              (assoc m table added-src-chan)))
                                          added-src
                                          added-src)]
    added-done-and-src-sub))

(defn resolve-deps
  [config]
  (let [dependencies (reduce-kv table-deps {} (:models config))]
    (chan-setup dependencies)))
