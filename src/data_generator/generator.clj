(ns data-generator.generator
  (:require [clj-time.coerce :refer [from-long to-sql-time to-long]]
            [clj-time.jdbc] ; For defaulting to joda objects when using jdbc via protol extension
            [data-generator.storage :as storage :refer [raw-query]]
            [incanter.distributions :as id] ;; functions are resovled in this namespace
            [incanter.core :refer [$=]] ;; macro is resolved at runtime in this namespace
            [taoensso.timbre :as timbre :refer [info error]]
            [clojure.math.combinatorics :as combo]
            [clojure.core.async :as a :refer [<!! <! >!! >! chan close! thread]]))

(defn inserter
  [insert-ch]
  (let [function (<!! insert-ch)]
    (if-not function
      (info "INSERTER SHUTTING DOWN")
      ;; (println "INSERTER SHUTTING DOWN")
      (do
        (function)
        (recur insert-ch)))))

(defn launch-inserters
  [insert-ch n]
  (a/merge (map (fn [_]
                  (thread (inserter insert-ch)))
                (range n))))

(defn date->long
  [value]
  (if (#{java.util.Date org.joda.time.DateTime} (class value))
    (to-long value)
    value))

(defn insert
  [config table model orig-model iteration out-chan]
  (let [row (storage/insert config table model)]
    ;; (println "ROW" row)
    (when out-chan
      (let [fixed-row (reduce-kv (fn [m k v]
                                   (assoc m k (date->long v)))
                                 row
                                 row)]
        (>!! out-chan {:src-item fixed-row 
                       :iteration iteration})))))

(defn run-fns
  ([config fn-coll model context]
   (run-fns config fn-coll model context {}))
  ([config fn-coll model context this]
   (if (empty? fn-coll)
     this
     (let [function (first fn-coll)
           context-list (mapcat identity (into [] context))
           arg-list (list* (:key function) this model :config config context-list)
           ;; arg-list (list* this model :config config context-list)
           results (try (apply (:fn function) arg-list)
                        (catch Exception e (do (println "RUNFNS-PRE" function (class arg-list) (first arg-list))
                                               (println "RUNFNS" this model context-list arg-list)
                                               (throw e))))
           {new-this :this new-models :models} results
           ;; new-this (function this model :count iteration :config config)
           new-fn-coll (rest fn-coll)]
       (recur config new-fn-coll new-models context new-this)))))

(defn coerce-dates
  [item data]
  (try (reduce-kv (fn [new-item field fdata]
                    (if (#{:timestamp-with-time-zone} (:type-norm fdata))
                      (let [value (field item)]
                        (if (nil? value)
                          (assoc new-item field value)
                          (if (#{java.util.Date org.joda.time.DateTime} (class value))
                            (assoc new-item field (to-sql-time value))
                            (assoc new-item field (-> value long to-sql-time)))))
                      new-item))
                  item
                  (:model data))
       (catch Exception e (do (println "Date coercion failure" item data)
                              (throw e)))))

(defn generate-model*
  [config dependencies table iterations insert-ch]
  (let [iteration (first iterations)
        fn-list (-> config :models table :fn-list)
        data (-> config :models table)
        src-pub (-> dependencies table :src-pub)]
    (if-not iteration
      (do
        (info table "iteration done, closing insert channel")
        (close! insert-ch))
      (let [item (run-fns config fn-list {} {:iteration iteration})
            skip? (->> item vals (some #{:none}))] ;; Select association returned nothing
        (when-not skip?
          (>!! insert-ch #(insert config table (coerce-dates item data) item iteration src-pub)))
        (recur config dependencies table (rest iterations) insert-ch)))))

(defn signal-model-complete
  [table listen-ch signal-ch pub-ch]
  (let [value (<!! listen-ch)]
    (info table "shutdown signal recieved" value)
    (if (nil? value)
      (do
        (info table "DONE INSERTING!")
        (info "If exists, shutting down" pub-ch)
        (when pub-ch
          (close! pub-ch))
        (close! signal-ch))
      (recur table listen-ch signal-ch pub-ch))))

(defn generate-model-from-source-helper
  [config table models insert-ch src-pub context seq-range]
  (if-let [n (first seq-range)]
    (let [fn-list (-> config :models table :fn-list)
          data (-> config :models table)
          new-context (assoc context :sequence n)
          item (run-fns config fn-list models new-context)
          new-models (update models table #(into [] (conj % (reduce-kv (fn [m k v]
                                                                         (assoc m k (date->long v)))
                                                                       item
                                                                       item))))
          probability-fn (-> config :models table :probability-fn)
          create? (-> probability-fn (apply (list* :create? {} new-models context)) :this :create?)
          skip? (->> item vals (some #{:none}))]
      (if-not (or skip? (not create?))
        (let [iteration (:iteration new-context)]
          (>!! insert-ch #(insert config table (coerce-dates item data) item iteration src-pub)))
        ;; (println "SKIPPING FROM SOURCE" table skip? create?)
        (info "SKIPPING FROM SOURCE" table skip? create?))
      (recur config table new-models insert-ch src-pub new-context (rest seq-range)))))

(defn calculate-quantity
  [config table models context]
  (let [quantity-fn (-> config :models table :quantity-fn)
        quantity (-> (quantity-fn :quantity {} models :iteration (:iteration context))
                     :this
                     :quantity)
        rounded  (try (Math/round (double quantity))
                      (catch Exception e (println "QUANTERROR" quantity table models context)))]
    rounded))

(defn generate-model-from-source-permutation
  [config table models iteration insert-ch src-pub permutations]
  (when-let [permutation (first permutations)]
    (let [foreach-keys (-> config :models table :foreach-keys)
          foreach-zipped (clojure.walk/prewalk date->long (zipmap foreach-keys permutation))
          new-models (merge foreach-zipped models)
          quantity (calculate-quantity config table models {:iteration iteration})]
      (generate-model-from-source-helper config
                                         table
                                         ;; src-item
                                         new-models
                                         insert-ch
                                         src-pub
                                         {:iteration iteration :quantity quantity}
                                         (range quantity))
      (recur config table models iteration insert-ch src-pub (rest permutations)))))

(defn generate-model-from-source*
  [config dependencies table src-table src-ch insert-ch]
  (let [{:keys [src-item iteration]} (<!! src-ch)
        src-pub (-> dependencies table :src-pub)]
    (if-not src-item
      (do
        (info table "recieved a nil! Closing insert channel")
        ;; (println table "recieved a nil! Closing insert channel")
        (close! insert-ch))
      (let [foreach-fns (-> config :models table :foreach-fns)]
        (if (seq foreach-fns)
          (generate-model-from-source-permutation config
                                                    table
                                                    {src-table src-item}
                                                    iteration
                                                    insert-ch
                                                    src-pub
                                                    ;; permutations
                                                    (apply combo/cartesian-product
                                                           (map #(% :dummy-key
                                                                    {}
                                                                    {src-table src-item}
                                                                    :iteration iteration
                                                                    :config config)
                                                                foreach-fns)))
          (let [quantity (calculate-quantity config table {src-table src-item} {:iteration iteration})]
            (generate-model-from-source-helper config
                                               table
                                               {src-table src-item}
                                               insert-ch
                                               src-pub
                                               {:iteration iteration :quantity quantity}
                                               (range quantity))))
        (recur config dependencies table src-table src-ch insert-ch)))))

(defn generate-model-from-query-helper
  [config dependencies table src-table src-seq insert-ch]
  (let [src-item (first src-seq)
        fn-list (-> config :models table :fn-list)
        data (-> config :models table)
        src-pub (-> dependencies table :src-pub)]
    (if-not src-item
      (do
        (info table "Done processing query! Closing insert channel")
        (close! insert-ch))
      (let [quantity (calculate-quantity config table {src-table src-item} {})]
        ;; (info src-table src-item)
        (generate-model-from-source-helper config
                                           table
                                           {src-table src-item}
                                           insert-ch
                                           src-pub
                                           {:quantity quantity}
                                           (range quantity))
        (recur config dependencies table src-table (rest src-seq) insert-ch)))))

(defn generate-model
  [config dependencies table]
  (let [insert-ch (chan 100)
        done-ch (-> dependencies table :done-chan)
        src-sub (-> dependencies table :src-sub)
        src-pub (-> dependencies table :src-pub)
        dependency-chans (->> dependencies
                              table
                              :table-dep
                              :select
                              (map #(-> dependencies
                                        %
                                        :done-chan)))
        start-chan (try (if (seq dependency-chans)
                          (a/merge dependency-chans 100) ;; Add buffer so models can close for sure 
                          (let [dummy-chan (chan)]
                            (close! dummy-chan) ;; Pre-close dummy chan so model starts immediately
                            dummy-chan))
                        (catch Exception e (do (println "DEPCHAN ERROR" dependency-chans)
                                               (throw e))))
        _ (<!! start-chan) ;; Block until all dependent tables are done
        inserting-done-ch (launch-inserters insert-ch 10)]
    (if src-sub
      (let [src-table (-> dependencies table :table-dep :source first)]
        ;; (println "Launching" table "with channel source")
        (info "Launching" table "with channel source")
        (generate-model-from-source* config dependencies table src-table src-sub insert-ch)
        ;; (println "Should be soon closeing src-pub of" table)
        (info "Should be soon closing src-pub of" table)
        (signal-model-complete table inserting-done-ch done-ch src-pub)
        true)
      (let [master (reduce-kv (fn [m field fdata]
                                (if (:master fdata)
                                  (merge m (:master fdata))
                                  m))
                              {}
                              (-> config :models table :model))]
        (if (:query master)
          (do
            (info "Launching" table "from query. " (:query master))
            (generate-model-from-query-helper config
                                              dependencies
                                              table
                                              (-> master :model keyword)
                                              (raw-query config (:query master))
                                              insert-ch)
            (info "Should be soon closing src-pub of" table)
            (signal-model-complete table inserting-done-ch done-ch src-pub)
            true)
          (do
            ;; (println "Launching" table "with iteration. Count:" (:count master))
            (info "Launching" table "with iteration. Count:" (:count master))
            (generate-model* config dependencies table (range (:count master)) insert-ch)
            ;; (println "Should be soon closeing src-pub of" table)
            (info "Should be soon closing src-pub-of" table)
            (signal-model-complete table inserting-done-ch done-ch src-pub)
            true)))))) ; Mark model done (when run via clojure.async.core/thread without returning nil

(defn signal-all-done
  [ch]
  (let [value (<!! ch)]
    (if (nil? value)
      ;; (println "ALL DONE!")
      (info "ALL DONE!")
      (recur ch))))

(defn generate
  [config dependencies]
  (let [model-done-chans (map (fn [[table _]]
                                (thread (generate-model config dependencies table)))
                              (:models config))
        ;; _ (println "LAUNCHED" (count model-done-chans) "THREADS FOR MODELS")
        _ (info "LAUNCHED" (count model-done-chans) "THREADS FOR MODELS")
        all-done-ch (a/merge model-done-chans)]
    (signal-all-done all-done-ch)
    config))

