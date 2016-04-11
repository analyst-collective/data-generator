(ns data-generator.field-generator
  (:require [clj-time.coerce :as c]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [data-generator.field-modifier :refer [wrap-modifier]]
            [data-generator.storage :refer [query
                                            query-filtered
                                            query-weighted
                                            query-filtered-weighted]]
            [incanter.distributions :as id]
            [incanter.core :refer [$=]]
            [faker.address]
            [faker.company]
            [faker.internet]
            [faker.lorem]
            [faker.name]
            [faker.phone_number]
            [taoensso.timbre :as timbre :refer [info warn error]]))

(defn coerce
  "Coerce keyword into field type.
  
  JSON requires string keys. JSON parsing turns string keys into keywords.
  Coercion of values into the final type is required"
  [value ftype]
  (try (if (nil? value)
         value
         (let [primitive-value (if (#{org.joda.time.DateTime java.util.Date} (class value))
                                 (c/to-long value)
                                 value)
               str-value (if (keyword? primitive-value)
                           (name primitive-value)
                           (str primitive-value))]
           (if (#{:text} ftype) ;; edn/read-string errors on strings that start with a digit
             str-value
             (if (#{:date :datetime :timestamp-with-time-zone} ftype)
               (if (re-find #"^\d+$" str-value)
                 (c/from-long (-> str-value edn/read-string long))
                 (c/from-string str-value))
               (let [read-value (edn/read-string str-value)]
                 (cond
                   (#{:integer :serial} ftype) (int read-value)
                   (#{:bigserial :bigint} ftype) (bigint read-value)
                   (#{:boolean} ftype) (if read-value
                                         true
                                         false)
                   (#{:real} ftype) (float read-value)
                   (#{:double :double-precision} ftype) (double read-value)))))))
       (catch Exception e (do (println "COERCE ERROR" value (class value) ftype)
                              (throw e)))))

(defn coerce-sql
  [value ftype]
  (let [coerced (coerce value ftype)]
    (if (and coerced (#{:date :datetime :timestamp-with-time-zone} ftype)) ;; Truth test for nil time
      (c/to-sql-time coerced)
      coerced)))

(defn str->primitive
  [string]
  (if ((complement instance?) java.lang.String string)
    string ;; not really a string
    (if-let [number (re-find #"^\d+(?:\.\d+)?$" string)]
      (Double/parseDouble number)
      (symbol string))))

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

(defmulti field-data* (fn [value _]
                        (:type value)))

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

;; http://stackoverflow.com/questions/9273333/in-clojure-how-to-apply-a-macro-to-a-list
;; The incanter macro "$=" is used to allow users to write infix notation formula's. Since we don't know
;; how many arguments (symbols) exist until runtime, the macro must be "functionized" so it can be evaluated
;; at runtime
(defmacro functionize
  [macro]
  `(fn [& args#] (eval (cons '~macro args#))))

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
          insert-x (s/replace sub-evaluated #"(^| )(x)($| )" (str "$1" (:iteration other) "$3"))
          insert-y (s/replace insert-x #"(^| )(y)($| )" (str "$1" (:sequence other) "$3"))
          insert-z (s/replace insert-y #"(^| )(z)($| )" (str "$1" (:quantity other) "$3"))
          equation-replaced (s/replace insert-z #"\s\^\s" " ** ")
          equation-split (s/split equation-replaced #"\s+")
          equation-resolved (map #(resolve-references % this model) equation-split)
          primitives (map str->primitive equation-resolved)
          calculated (try (if (> (count primitives) 1) ; If there's only 1 primitive, no calculation required
                            (apply (functionize $=) primitives) ; this allows strings to be returned as well
                            (first primitives))
                          (catch Exception e (do (println "PROBLEM" equation primitives this model)
                                                 (throw e))))]
      calculated)))

(defn randomize-value
  "Apply random offset (randomness) to given value"
  [value randomness]
  (if-not randomness
    value
    (let [multiplier ((rand-nth [+ -]) 1 (rand randomness))]
      (* value multiplier))))

(defmethod field-data* "formula"
  [value type-norm]
  (let [properties (:properties value)]
    (fn gf_formula [mkey this model & more]
      ;; (println mkey value)
      (try
        (let [other (apply hash-map more)
              properties (current-properties properties (:iteration other))
              calculated (calculate-formula (:equation properties) this model more)
              randomness (resolve-references (:randomness properties) this model)
              randomized (randomize-value calculated randomness)]
          {:this (assoc this mkey randomized)
           :models model})
        (catch Exception e (do (println "Formula Error" mkey this model)
                               (throw e)))))))

(defmethod field-data* "distribution"
  [value type-norm]
  (let [dist (-> value :properties :type)
        arguments (or (-> value :properties :args) [])
        resolved (resolve (symbol "id" dist))]
    (fn gf_dist [mkey this model & more]
      (try
        (let [resolved-args (map #(calculate-formula % this model more)
                                 arguments)
              real-fn (if (empty? resolved-args)
                        resolved
                        (apply partial (cons resolved resolved-args)))]
          {:this (assoc this mkey (id/draw (real-fn)))
           :models model})
        (catch Exception e (do (println "Dist Error" mkey this model)
                               (throw e)))))))

(defmethod field-data* "faker"
  [value type-norm]
  (let [ns-and-function (:function value)
        split (s/split ns-and-function #"\.")
        ns (str "faker." (first split))
        func (second split)
        resolved (resolve (symbol ns func))]
    (fn gf_faker [mkey this model & more]
      (try
        (let [args (:args value)
              resolved-args (map #(resolve-references % this model) (or args []))
              real-fn (if-not (seq resolved-args)
                        resolved
                        (apply partial (cons resolved resolved-args)))]
          {:this (assoc this
                        mkey
                        (real-fn))
           :models model})
        (catch Exception e (do (println "Faker error" mkey this model)
                               (throw e)))))))

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
                                              diff (- maximum minimum)]
                                          {:this (assoc this mkey (-> diff rand int (+ minimum)))
                                           :models model})) 
      (#{:real} type-norm) (fn gf_range [mkey this model & more]
                             ;; (println mkey value)
                             (let [maximum (resolve-references maximum this model)
                                   minimum (resolve-references minimum this model)
                                   diff (- maximum minimum)]
                               {:this (assoc this mkey  (-> diff rand float (+ minimum)))
                                :models model}))
      (#{:double-precision} type-norm) (fn gf_range [mkey this model & more]
                               ;; (println mkey value)
                               (let [maximum (resolve-references maximum this model)
                                     minimum (resolve-references minimum this model)
                                     diff (- maximum minimum)]
                                 {:this (assoc this mkey (-> diff rand (+ minimum)))
                                  :models model}))
      (#{:timestamp-with-time-zone} type-norm) (fn gf_range [mkey this model & more]
                                                 (let [maximum (resolve-references maximum this model)
                                                       minimum (resolve-references minimum this model)
                                                       diff (-' maximum minimum)]
                                                   ;; (println "timestamp range" minimum maximum)
                                                   {:this (assoc this mkey (-> diff rand long (+ minimum)))
                                                    :models model})))))

(defmethod field-data* "enum"
  [value type-norm]
  (let [weights (:weights value)]
    (fn gf_enum [mkey this model & more]
      (try
        (let [options (mapcat
                       (fn [[k v]]
                         (repeat (resolve-references v this model)
                                 (coerce (resolve-references (name k) this model) type-norm)))
                       weights)
              chosen (try (rand-nth options)
                          (catch Exception e (do (println "OPTIONS" options weights)
                                                 (throw e))))]
          (try {:this (assoc this mkey (resolve-references chosen this model))
                :models model}
               (catch Exception e (do (println "THIS MKEY" this mkey chosen)
                                      (throw e)))))
        (catch Exception e2 (do (println "ENUM ERROR" mkey this model)
                                (throw e2)))))))

(defmethod field-data* "autoincrement"
  [value type-norm]
  (fn gf_autoincrement [mkey this model & more]
    {:this this
     :models model}))

(defmethod field-data* "concat"
  [value type-norm]
  (fn gf_concat [mkey this model & more]
    (try
      (let [values (-> value :properties :values)
            resolved-values (map #(resolve-references % this model) values)
            joined (s/join "" resolved-values)]
        {:this (assoc this mkey joined)
         :models model})
      (catch Exception e (do (println "Concat Error" mkey this model)
                             (throw e))))))

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

(defn date->long
  [value]
  (if (#{java.util.Date org.joda.time.DateTime} (class value))
    (c/to-long value)
    value))

(defn field-data
  [config fdata]
  (let [type-norm (:type-norm fdata)
        association? (-> fdata :type s/lower-case (= "association"))
        val-type (-> fdata :value :type)
        modifier (get-in fdata [:value :modifier])]
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
          (let [weight (-> fdata :value :weight keyword)]
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
                                                                                        last
                                                                                        keyword)
                                              :else %))))
                    filter-field (when filter-prepped
                                   (some #(and (keyword? %) %) filter-prepped))
                    filter-type (when filter-field
                                  (-> config :models table :model filter-field :type-norm))
                    other (apply hash-map more)
                    database (-> other :config :database)
                    pool (-> other :config :pool)
                    resolved-where (map #(resolve-references % this model) filter-prepped)
                    primitives-where (map str->primitive resolved-where)
                    resolved-value (let [no-op [(first primitives-where) (last primitives-where)]]
                                     (first (remove keyword? no-op)))
                    normalized-where (try (replace {resolved-value (coerce-sql resolved-value filter-type)}
                                                   primitives-where)
                                          (catch Exception e (do (println "NORMALIZE FILTER ERROR" filter-criteria filter-prepped primitives-where model)
                                                                 (throw e))))
                    result (first (cond
                                    (and weight filter-prepped) (query-filtered-weighted config
                                                                                         table
                                                                                         normalized-where
                                                                                         weight
                                                                                         field)
                                    weight (query-weighted config table weight field)
                                    filter-prepped (query-filtered config table normalized-where)
                                    :else (query config table)))
                    fixed-dates (reduce-kv (fn [m k v]
                                           (assoc m k (date->long v)))
                                         result
                                         result)
                    new-models (assoc model table fixed-dates)]
                (if-not result
                  {:this (assoc this mkey :none)
                   :models new-models}
                  {:this (assoc this mkey (field result))
                   :models new-models}))))))
      (wrap-modifier modifier (field-data* (:value fdata) type-norm)))))
