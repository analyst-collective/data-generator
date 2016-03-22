(ns data-generator.generator-factory
  (:require [clj-time.coerce :as c]
            [clojure.string :as s]
            [taoensso.timbre :as timbre :refer [info warn error]]
            [clojure.java.jdbc :as j]
            [sqlingvo.core :as sql]
            [sqlingvo.db :refer [postgresql sqlite]]
            [incanter.distributions :as id]
            [incanter.core :refer [$=]]
            [faker.address]
            [faker.company]
            [faker.internet]
            [faker.lorem]
            [faker.name]
            [faker.phone_number]))

(def pg (postgresql))

(defn add-cumulative-tag
  [field]
  (-> field name (str "_cumulative") keyword))

(defn filter->where-criteria
  [[a op b]]
  (list op a b))

(defn query-filtered
  [table filter-list]
  (sql/sql
   (sql/select
       pg
       [:* (sql/as '(random) :random_col_for_sorting)]
       (sql/from table)
                                        ; TODO support OR/AND criteria
       (sql/where (filter->where-criteria filter-list))
       (sql/order-by :random_col_for_sorting)
       (sql/limit 1))))

(defn query-weighted
  [table weighted-field pk]
  (let [cumulative (add-cumulative-tag weighted-field)]
    (sql/sql
     (sql/with pg [:temp (sql/select pg [:* (sql/as
                                             `(/ ((over
                                                   (sum ~weighted-field)
                                                   (order-by ~pk)))
                                                 (cast ~(sql/select
                                                           pg
                                                           [`(sum ~weighted-field)]
                                                         (sql/from table)) :float))
                                             cumulative)] (sql/from table))]
       (sql/select
           pg
           [:*]
         (sql/from :temp)
         (sql/where `(> ~cumulative ~(rand)))
         (sql/order-by cumulative)
         (sql/limit 1))))))

(defn query-filtered-weighted
  [table filter-list weighted-field pk]
  (let [cumulative (add-cumulative-tag weighted-field)
        ;; _ (println "FILTER LIST" filter-list)
        where-statement (filter->where-criteria filter-list)]
    ;; (println "FINAL WHERE STATEMENT" where-statement)
    ;; (println "FIRST TYPE" (class (first where-statement)))
    ;; (println "TABLE" table)
    (sql/sql
     (sql/with pg [:temp (sql/select
                             pg
                             [:* (sql/as
                                  `(/ ((over
                                        (sum ~weighted-field)
                                        (order-by ~pk)))
                                      (cast ~(sql/select pg [`(sum ~weighted-field)]
                                              (sql/from table)
                                              (sql/where where-statement)) :float))
                                  cumulative)]
                           (sql/from table)
                           (sql/where where-statement))]
       (sql/select
           pg
           [:*]
         (sql/from :temp)
         (sql/where `(> ~cumulative ~(rand)))
         (sql/order-by cumulative)
         (sql/limit 1))))))

(defn query
  [table]
  (sql/sql
   (sql/select
       pg
       [:* (sql/as '(random) :random_col_for_sorting)]
       (sql/from table)
       (sql/order-by :random_col_for_sorting)
       (sql/limit 1))))



;; http://stackoverflow.com/questions/9273333/in-clojure-how-to-apply-a-macro-to-a-list
;; The incanter macro "$=" is used to allow users to write infix notation formula's. Since we don't know
;; how many arguments (symbols) exist until runtime, the macro must be "functionized" so it can be evaluated
;; at runtime
(defmacro functionize
  [macro]
  `(fn [& args#] (eval (cons '~macro args#))))

(defn randomize-value
  "Apply random offset (randomness) to given value"
  [value randomness]
  (if-not randomness
    value
    (let [multiplier ((rand-nth [+ -]) 1 (rand randomness))]
      (* value multiplier))))

(defn coerce
  "Coerce keyword into field type.
  
  JSON requires string keys. JSON parsing turns string keys into keywords.
  Coercion of values into the final type is required"
  [value type]
  (if (nil? value)
    value
    (let [str-value (if (keyword? value)
                      (name value)
                      (str value))]
      (cond
        (#{:integer :serial} type) (Integer/parseInt str-value)
        (#{:bigserial :bigint} type) (bigint str-value)
        (#{:boolean} type) (Boolean/valueOf str-value)
        (#{:real} type) ((Float/parseFloat str-value))
        (#{:double} type) (Double/parseDouble str-value)
        (#{:date :datetime :timestamp-with-time-zone} type) (c/from-long (Integer/parseInt value))
        (#{:text} type) str-value))))

(defn resolve-references
  [raw this models]
  ;; (println raw this models)
  (if (instance? java.lang.String raw)
    (if-let [nil-val (re-find #"\$\$(?:null|nil)" raw)]
      nil
      (if-let [field (->> raw (re-find #"^\$\$self\.(.+)$") second keyword)]
        (field this)
        (if-let [[model field] (->> raw (re-find #"^\$([^\$^\.]*)\.(.+)$") rest (map keyword) seq)]
          (-> models model field)
          (if-let [match (->> raw (re-find #"\$\$mult\$([^$\.]*)\$(\d+\.\d+)\.(\w+)") rest seq)]
            (let [
                  ;; _ (println "MULT MATCH" raw match)
                  index (-> match second (#(Float/parseFloat %)) int)
                  field (keyword (last match))
                  model (keyword (first match))]
              ;; (println "MULT GET" models model index field)
              (-> models model (get index) field))
              raw))))
    raw))

(defn current-properties
  "Returns the current properties based on a coll, and a count"
  [properties count]
  (if (instance? clojure.lang.IPersistentMap properties)
    properties
    ; sort by :start in reverse (nil let). Take highest :start less than "count", or the first nil :start
    ; Return full map
    (let [buckets (reverse (sort-by #(:start %) properties))
          current-props (some #(and (or (nil? (:start %))
                                        (> count (:start %))) %) properties)]
      current-props)))

(defn str->primitive
  [string]
  (if ((complement instance?) java.lang.String string)
    string ;; not really a string
    (if-let [number (re-find #"^\d+(?:\.\d+)?$" string)]
      (Double/parseDouble number)
      (symbol string))))

(defn calculate-formula
  [equation this model more]
  (if-not (instance? java.lang.String equation)
    equation ; nothing to calculate
    (let [sub-equations (re-seq #"\(([^\)]+)\)" equation)
          sub-evaluated (reduce (fn [eq [to-replace to-calc]]
                                  (let [calculated (calculate-formula to-calc this model more)
                                        replaced (s/replace eq to-replace (str calculated))]
                                    replaced))
                                equation
                                sub-equations)
          other (apply hash-map more)
          ;; properties (current-properties properties (:count other))
          insert-x (s/replace sub-evaluated #"(^| )(x)($| )" (str "$1" (:iteration other) "$3"))
          insert-y (s/replace insert-x #"(^| )(y)($| )" (str "$1" (:sequence other) "$3"))
          insert-z (s/replace insert-y #"(^| )(z)($| )" (str "$1" (:quantity other) "$3"))
          equation-replaced (s/replace insert-z #"\s\^\s" " ** ")
          equation-split (s/split equation-replaced #"\s+")
          equation-resolved (map #(resolve-references % this model) equation-split)
          primitives (map str->primitive equation-resolved)
          ;; _ (info "primitives" (doall primitives))
          
          calculated (try (if (> (count primitives) 1) ; If there's only 1 primitive, no calculation required
                            (apply (functionize $=) primitives) ; this allows strings to be returned as well
                            (first primitives))
                          (catch Exception e (do (println "PROBLEM" primitives)
                                                 (throw e))))]
      calculated)))

(defmulti field-data* (fn [value _]
                        (:type value)))

(defmethod field-data* "case"
  [value type-norm]
  (let [result-fns (reduce-kv (fn [m res rvalue]
                                (assoc m res (field-data* rvalue type-norm)))
                              {}
                              (:branches value))]
    (fn gf_case [mkey this model & more]
      ;; (println mkey value)
      (try (let [evaluated (calculate-formula (:check value) this model more)
                 chosen-fn ((-> evaluated str keyword) result-fns)
                 args (list* mkey this model more)]
             (try (apply chosen-fn args)
                  (catch Exception e (do (println "CASE ERROR" value evaluated chosen-fn args)
                                         (throw e)))))
           (catch Exception e2 (do (println "Case Error" mkey this model)
                                   (throw e2)))))))

(defn uuid []
  (str (java.util.UUID/randomUUID)))

(defmethod field-data* "uuid"
  [value type-norm]
  (fn gf_uuid [mkey this model & more]
    ;; (println mkey value)
    (try
      (let [generated (uuid)]
        {:this (assoc this mkey generated)
         :models model})
      (catch Exception e (do (println "UUID Error" mkey this model)
                             (throw e))))))

(defmethod field-data* "concat"
  [value type-norm]
  (fn gf_concat [mkey this model & more]
    ;; (println mkey value)
    (try
      (let [values (-> value :properties :values)
            resolved-values (map #(resolve-references % this model) values)
            joined (s/join "" resolved-values)]
        {:this (assoc this mkey joined)
         :models model})
      (catch Exception e (do (println "Concat Error" mkey this model)
                             (throw e))))))

(defmethod field-data* "autoincrement"
  [value type-norm]
  (fn gf_autoincrement [mkey this model & more]
    ;; (println mkey value)
    {:this this
     :models model}))

(defmethod field-data* "enum"
  [value type-norm]
  (let [weights (:weights value)]
    (fn gf_enum [mkey this model & more]
      ;; (println mkey value)
      (try
        (let [options (mapcat
                       (fn [[k v]]
                         (repeat (resolve-references v this model)
                                 ;; (resolve-references (coerce k type-norm) this model)
                                 (coerce (resolve-references (name k) this model) type-norm)
                                 ))
                       weights)
              chosen (try (rand-nth options)
                          (catch Exception e (do (println "OPTIONS" options weights)
                                                 (throw e))))]
          ;; (println "OPTIONS nth" chosen)
          (try {:this (assoc this mkey (resolve-references chosen this model))
                :models model}
               (catch Exception e (do (println "THIS MKEY" this mkey chosen)
                                      (throw e)))))
        (catch Exception e2 (do (println "ENUM ERROR" mkey this model)
                                (throw e2)))))))

(defmethod field-data* "range"
  [value type-norm]
  (let [minimum (or (-> value :properties :min) 0)
        maximum (-> value :properties :max)]
    (cond
      (#{:bigserial :bigint} type-norm) (fn gf_range [mkey this model & more]
                                              ;; (println mkey value)
                                              (let [maximum (resolve-references maximum this model)
                                                    minimum (resolve-references minimum this model)
                                                    diff (try (-' maximum minimum)
                                                              (catch Exception e (do (println "range exception"
                                                                                              maximum
                                                                                              minimum
                                                                                              mkey this)
                                                                                     (throw e))))]
                                                {:this (assoc this mkey (-> diff
                                                                            rand bigint
                                                                            (+' minimum)))
                                                 :models model}))
      (#{:integer :serial} type-norm) (fn gf_range [mkey this model & more]
                                        ;; (println "RANGE TEST" mkey this model maximum minimum)
                                        (let [maximum (resolve-references maximum this model)
                                              minimum (resolve-references minimum this model)
                                              diff (-' maximum minimum)]
                                          {:this (assoc this mkey (-> diff rand int (+ minimum)))
                                           :models model}))
      (#{:real} type-norm) (fn gf_range [mkey this model & more]
                             ;; (println mkey value)
                             (let [maximum (resolve-references maximum this model)
                                   minimum (resolve-references minimum this model)
                                   diff (-' maximum minimum)]
                               {:this (assoc this mkey  (-> diff rand float (+ minimum)))
                                :models model}))
      (#{:double} type-norm) (fn gf_range [mkey this model & more]
                               ;; (println mkey value)
                               (let [maximum (resolve-references maximum this model)
                                     minimum (resolve-references minimum this model)
                                     diff (-' maximum minimum)]
                                 {:this (assoc this mkey (-> diff rand (+ minimum)))
                                  :models model})))))

(defmethod field-data* "faker"
  [value type-norm]
  (let [ns-and-function (:function value)
        split (s/split ns-and-function #"\.")
        ns (str "faker." (first split))
        func (second split)
        resolved (resolve (symbol ns func))
        args (:args value)
        real-fn (if-not (seq args)
                  resolved
                  (apply partial (cons resolved args)))]
    (fn gf_faker [mkey this model & more]
      (try
        {:this (assoc this mkey (real-fn))
         :models model}
        (catch Exception e (do (println "Faker error" mkey this model)
                               (throw e)))))))

(defmethod field-data* "distribution"
  [value type-norm]
  (let [dist (-> value :properties :type)
        ;; _ (println "DIST" dist)
        ;; _ (println "SYMBOL" (symbol "id" dist))
        arguments (or (-> value :properties :args) [])
        resolved (resolve (symbol "id" dist))]
    ;; (println "RESOLVED" resolved)
    (fn gf_dist [mkey this model & more]
      ;; (println mkey value)
      (try
        (let [resolved-args (map #(calculate-formula % this model more) ;#(resolve-references % this model)
                                 arguments)
              ;; _ (println "RESOLVED ARGS" resolved-args)
              real-fn (if (empty? resolved-args)
                        resolved
                        (apply partial (cons resolved resolved-args)))]
          {:this (assoc this mkey (id/draw (real-fn)))
           :models model})
        (catch Exception e (do (println "Dist Error" mkey this model)
                               (throw e)))))))

(defmethod field-data* "formula"
  [value type-norm]
  (let [properties (:properties value)]
    (fn gf_formula [mkey this model & more]
      ;; (println mkey value)
      (try
        (let [
              other (apply hash-map more)
              properties (current-properties properties (:iteration other))
              ;; insert-x (s/replace (:equation properties) #"x" (-> other :count str))
              ;; equation-replaced (s/replace insert-x #"\s\^\s" " ** ")
              ;; equation-split (s/split equation-replaced #"\s+")
              ;; equation-resolved (map #(resolve-references % this model) equation-split)
              ;; primitives (map str->primitive equation-resolved)
              ;; calculated (apply (functionize $=) primitives)
              calculated (calculate-formula (:equation properties) this model more)
              randomness (resolve-references (:randomness properties) this model)
              randomized (randomize-value calculated randomness)
              ]
          {:this (assoc this mkey randomized)
           :models model})
        (catch Exception e (do (println "Formula Error" mkey this model)
                               (throw e)))))))

(defn normalize-filter
  [type-norm value]
  (cond
    (#{:timestamp-with-time-zone} type-norm) (-> value long c/to-sql-time)
    :else value))

(defn field-data
  [config fdata]
  ;; (println fdata)
  (let [type-norm (:type-norm fdata)
        association? (-> fdata :type s/lower-case (= "association"))
        val-type (-> fdata :value :type)]
    (if association?
      (let [field (-> fdata :value :field keyword)
            table (or (-> fdata :value :model keyword)
                      (-> fdata :master :model keyword))
            master? (:master fdata)]
        (if master?
          (fn gf_master_assoc [mkey this models & more]
            ;; (println models table field)
            {:this (assoc this mkey (-> models table field))
             :models models})
          (let [weight (-> fdata :value :weight keyword)
                
                ]
            ;; (println "PREPPED" filter-prepped)
            (fn gf_query_assoc [mkey this model & more]
              (let [filter-criteria (-> fdata :value :filter)
                    filter-prepped (when filter-criteria
                                     (->> filter-criteria
                                          (#(s/split %  #"\s+"))
                                          (map
                                           #(cond
                                              ((complement instance?) java.lang.String %) %
                                              (re-find #"^\$([^\$^\.]*)\.(.+)$" %) (->> %
                                                                                        (re-find
                                                                                         #"^\$[^\$^\.]*\.(.+)$")
                                                                                        ;; #"^\$model\.(.+)$")
                                                                                        last
                                                                                        keyword)
                                              :else %))))
                    filter-field (when filter-prepped
                                   (some #(and (keyword? %) %) filter-prepped))
                    ;; _ (println "FILTER FIELD" filter-field)
                    filter-type (when filter-field
                                  (-> config :models table :model filter-field :type-norm))
                    other (apply hash-map more)
                    database (-> other :config :database)
                    ;; _ (println "PREPPED" filter-prepped)
                    resolved-where (map #(resolve-references % this model) filter-prepped)
                    ;; _ (println "RESOLVED" resolved-where)
                    primitives-where (map str->primitive resolved-where)
                    ;; _ (println "RESOLVED WHERE" resolved-where)
                    ;; _ (println "RESOLVED TYPE" (class (second resolved-where)))
                    resolved-value (some #(and (number? %) %) primitives-where)
                    normalized-where (replace {resolved-value (normalize-filter filter-type resolved-value)}
                                              primitives-where)
                    query-statement (cond
                                      (and weight filter-prepped) (query-filtered-weighted table
                                                                                           ;; filter-prepped
                                                                                           ;; primitives-where
                                                                                           normalized-where
                                                                                           weight
                                                                                           field)
                                      weight (query-weighted table weight field)
                                      filter-prepped (query-filtered table filter-prepped)
                                      :else (query table))
                    ;; _ (println "STATEMENT" query-statement)
                    result (first (j/query database query-statement))
                    new-models (assoc model table result)]
                (if-not result
                  {:this (assoc this mkey :none)
                   :models new-models}
                  {:this (assoc this mkey (field result))
                   :models new-models}))))))
      (field-data* (:value fdata) type-norm))))

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
                            (throw (Exception. (str "Circular dependency!")))))
           new-mdata (dissoc mdata (:key new-item))
           new-deps (remove-field-dep deps table (:key new-item))
           new-fn-list (conj fn-list new-item) #_(conj fn-list
                                           (partial (:fn new-item)
                                                    (:key new-item)))]
       (recur config table new-mdata new-deps new-fn-list)))))

(defn association-data
  "Adds quantifier function and likelyhood funciton"
  [data]
  (reduce-kv (fn [agg field fdata]
               (let [field-type (:type fdata)
                     master-association? (and (= "association" field-type)
                                             (:master fdata))]
                 (if-not master-association?
                   agg
                   (let [quantity (-> fdata :master :quantity)
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
                         with-fns (assoc agg :quantity-fn quantity-fn :probability-fn probability-fn)]
                     with-fns))))
             data
             (:model data)))

(defn generators
  [config dependencies]
  (let [models (:models config)
        ;; _ (println models)
        new-models (reduce-kv (fn [m table data]
                                (let [fn-list (build-model-generator config table data dependencies)
                                      added-association-data (association-data data)
                                      new-data (assoc added-association-data :fn-list fn-list)]
                                  (assoc m table new-data)))
                              models
                              models)]
    (assoc config :models new-models)))


