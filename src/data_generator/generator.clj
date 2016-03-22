(ns data-generator.generator
  (:require [sqlingvo.core :as sql]
            [sqlingvo.db :refer [postgresql sqlite]]
            [clojure.java.jdbc :as j]
            [clj-time.coerce :refer [from-long to-sql-time]]
            [incanter.distributions :as id] ;; functions are resovled in this namespace
            [incanter.core :refer [$=]] ;; macro is resolved at runtime in this namespace
            [clojure.core.async :as a :refer [<!! <! >!! >! chan close! thread]]))

(def pg (postgresql))
(def lite (sqlite))

(defn inserter
  [insert-ch]
  (let [function (<!! insert-ch)]
    (if-not function
      (println "INSERTER SHUTTING DOWN")
      (do
        (function)
        (recur insert-ch)))))

(defn launch-inserters
  [insert-ch n]
  (a/merge (map (fn [_]
                  (thread (inserter insert-ch)))
                (range n))))

(defn insert
  [config table model orig-model iteration out-chan]
  (let [db-spec (:database config)
        insert-statement (sql/sql (sql/insert pg table []
                                       (sql/values [model])))
        prepped [(first insert-statement) (rest insert-statement)]
        ;; _ (println "INSERT PREPPED" prepped)
        row (try (apply j/db-do-prepared-return-keys db-spec prepped)
                 (catch Exception e (do (println "TROUBLE" prepped)
                                         (throw e))))]
    ;; (println "ROW" row)
    (when out-chan
      (>!! out-chan {:src-item #_row (merge row orig-model) ;; Merge puts dates back into long format
                                                            ;; but keeps autoincrents from db
                     :iteration iteration}))))

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
  (reduce-kv (fn [new-item field fdata]
               (if (#{:timestamp-with-time-zone} (:type-norm fdata))
                 (assoc new-item field (-> item field long to-sql-time) #_(-> item field long from-long))
                 new-item))
             item
             (:model data)))

(defn generate-model*
  [config dependencies table iterations insert-ch]
  (let [iteration (first iterations)
        fn-list (-> config :models table :fn-list)
        data (-> config :models table)
        src-pub (-> dependencies table :src-pub)]
    (if-not iteration
      (do
        (println table "iteration done, closing insert channel")
        (close! insert-ch))
      (let [item (run-fns config fn-list {} {:iteration iteration})
            skip? (->> item vals (some #{:none}))] ;; Select association returned nothing
        (when-not skip?
          (>!! insert-ch #(insert config table (coerce-dates item data) item iteration src-pub)))
        (recur config dependencies table (rest iterations) insert-ch)))))

(defn signal-model-complete
  [table listen-ch signal-ch pub-ch]
  (let [value (<!! listen-ch)]
    (println table "shutdown signal recieved" value)
    (if (nil? value)
      (do
        (println table "DONE INSERTING!")
        (println "If exists, shutting down" pub-ch)
        (when pub-ch
          (close! pub-ch))
        (close! signal-ch))
      (recur table listen-ch signal-ch pub-ch))))

(defn generate-model-from-source-helper
  [config table src-item models insert-ch src-pub context seq-range]
  (if-let [n (first seq-range)]
    (let [fn-list (-> config :models table :fn-list)
          data (-> config :models table)
          new-context (assoc context :sequence n)
          item (run-fns config fn-list models new-context)
          new-models (update models table #(into [] (conj % item)))
          probability-fn (-> config :models table :probability-fn)
          create? (-> probability-fn (apply (list* :create? {} new-models context)) :this :create?)
          skip? (->> item vals (some #{:none}))]
      (when-not (or skip? (not create?))
        (let [iteration (:iteration new-context)]
          (>!! insert-ch #(insert config table (coerce-dates item data) item iteration src-pub))))
      (recur config table src-item new-models insert-ch src-pub new-context (rest seq-range)))))

(defn generate-model-from-source*
  [config dependencies table src-table src-ch insert-ch]
  (let [{:keys [src-item iteration]} (<!! src-ch)
        src-pub (-> dependencies table :src-pub)]
    (if-not src-item
      (do
        (println table "recieved a nil! Closing insert channel")
        (close! insert-ch))
      (let [quantity-fn (-> config :models table :quantity-fn)
            quantity (-> (quantity-fn :quantity {} {src-table src-item} :iteration iteration)
                         :this
                         :quantity)
            rounded  (Math/round (double quantity))]
        (generate-model-from-source-helper config
                                           table
                                           src-item
                                           {src-table src-item}
                                           insert-ch
                                           src-pub
                                           {:iteration iteration :quantity rounded}
                                           (range rounded))
        (recur config dependencies table src-table src-ch insert-ch)))))

(defn generate-model
  [config dependencies table]
  (let [insert-ch (chan 1000)
        inserting-done-ch (launch-inserters insert-ch 10)
        done-ch (-> dependencies table :done-chan)
        src-sub (-> dependencies table :src-sub)
        src-pub (-> dependencies table :src-pub)]
    (if src-sub
      (let [src-table (-> dependencies table :table-dep :source first)]
        (println "Launching" table "with channel source")
        (generate-model-from-source* config dependencies table src-table src-sub insert-ch)
        (println "Should be soon closeing src-pub of" table)
        (signal-model-complete table inserting-done-ch done-ch src-pub))
      (let [master (reduce-kv (fn [m field fdata]
                                (if (:master fdata)
                                  (merge m (:master fdata))
                                  m))
                              {}
                              (-> config :models table :model))
            iterations (range (:count master))]
        (println "Launching" table "with iteration. Count:" (:count master))
        (generate-model* config dependencies table iterations insert-ch)
        (println "Should be soon closeing src-pub of" table)
        (signal-model-complete table inserting-done-ch done-ch src-pub)
        true)))) ; Mark model done (when run via clojure.async.core/thread without returning nil

(defn signal-all-done
  [ch]
  (let [value (<!! ch)]
    (if (nil? value)
      (println "ALL DONE!")
      (recur ch))))

(defn generate
  [config dependencies]
  (let [model-done-chans (map (fn [[table _]]
                                (thread (generate-model config dependencies table)))
                              (:models config))
        _ (println "LAUNCHED" (count model-done-chans) "THREADS FOR MODELS")
        all-done-ch (a/merge model-done-chans)]
    (signal-all-done all-done-ch)
    config))

