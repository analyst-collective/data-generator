(ns data-generator.generator-factory
  (:require [clj-time.coerce :as c]
            [clojure.string :as s]
            [incanter.distributions :as id]
            [incanter.core :refer [$=]]
            [faker.address]
            [faker.company]
            [faker.internet]
            [faker.lorem]
            [faker.name]
            [faker.phone_number]))


;; http://stackoverflow.com/questions/9273333/in-clojure-how-to-apply-a-macro-to-a-list
;; The incanter macro "$=" is used to allow users to write infix notation formula's. Since we don't know
;; how many arguments (symbols) exist until runtime, the macro must be "functionized" so it can be evaluated
;; at runtime
(defmacro functionize
  [macro]
  `(fn [& args#] (eval (cons '~macro args#))))

(defn coerce
  "Coerce keyword into field type.
  
  JSON requires string keys. JSON parsing turns string keys into keywords.
  Coercion of values into the final type is required"
  [value type]
  (let [str-value (name value)]
    (cond
      (#{:integer :serial} type) (Integer/parseInt str-value)
      (#{:bigserial :biginteger} type) (bigint str-value)
      (#{:boolean} type) (Boolean/valueOf str-value)
      (#{:real} type) ((Float/parseFloat str-value))
      (#{:double} type) (Double/parseDouble str-value)
      (#{:date :datetime :timestamp-with-time-zone} type) (c/from-long (Integer/parseInt value))
      (#{:text} type) str-value)))

(defn resolve-references
  [raw this model]
  (if (instance? java.lang.String raw)
    (if-let [field (->> raw (re-find #"^\$self\.(.+)$") second keyword)]
      (field this)
      (if-let [field (->> raw (re-find #"^\$model\.(.+)$") second keyword)]
        (field model)
        raw))
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
  (if-let [number (re-find #"^\d+(?:\.\d+)?$" string)]
    (Double/parseDouble number)
    (symbol string)))

;; TODO make this a mutli-method to make clearer and allow for extensibility
(defn field-data
  [fdata]
  (let [type-norm (:type-norm fdata)
        val-type (-> fdata :value :type)]
    (case val-type
      "autoincrement" (fn [key this model & more]
                        this) ; No need to do anything. The db will generate this field on insert
      "enum" (let [weights (-> fdata :value :weights)]
               (fn [key this model & more]
                 (let [options (mapcat
                                (fn [[k v]]
                                  (repeat (resolve-references v this model)
                                          (resolve-references (coerce k type-norm) this model)))
                                weights)]
                   (assoc this key (resolve-references (rand-nth options) this model)))))
      "range" (let [minimum (or (-> fdata :value :properties :min) 0)
                    maximum (-> fdata :value :properties :max)]
                (cond
                  (#{:bigserial :biginteger} type-norm) (fn [key this model & more]
                                                     (let [maximum (resolve-references maximum this model)
                                                           minimum (resolve-references minimum this model)
                                                           diff (-' maximum minimum)]
                                                       (assoc this
                                                              key
                                                              (-> diff rand bigint (+' minimum)))))
                  (#{:integer :serial} type-norm) (fn [key this model & more]
                                               (let [maximum (resolve-references maximum this model)
                                                     minimum (resolve-references minimum this model)
                                                     diff (-' maximum minimum)]
                                                 (assoc this
                                                        key
                                                        (-> diff rand int (+ minimum)))))
                  (#{:real} type-norm) (fn [key this model & more]
                                    (let [maximum (resolve-references maximum this model)
                                          minimum (resolve-references minimum this model)
                                          diff (-' maximum minimum)]
                                      (assoc this
                                             key
                                             (-> diff rand float (+ minimum)))))
                  (#{:double} type-norm) (fn [key this model & more]
                                      (let [maximum (resolve-references maximum this model)
                                            minimum (resolve-references minimum this model)
                                            diff (-' maximum minimum)]
                                        (assoc this
                                               key
                                               (-> diff rand (+ minimum)))))))
      "faker" (let [ns-and-function (-> fdata :value :function)
                    split (s/split ns-and-function #"\.")
                    ns (str "faker." (first split))
                    func (second split)
                    resolved (resolve (symbol ns func))
                    args (-> fdata :value :args)
                    real-fn (if-not (seq args)
                              resolved
                              (apply partial (cons resolved args)))]
                (fn [key this model & more]
                  (assoc this
                         key
                         (real-fn))))
      "distribution" (let [dist (-> fdata :value :properties :type)
                           arguments (or (-> fdata :value :properties :args) [])
                           resolved (resolve (symbol "id" dist))
                           _ (println fdata dist resolved)
                           real-fn (if-not (seq arguments)
                                     resolved
                                     (apply partial (cons resolved arguments)))]
                       (fn [key this model & more]
                         (let [resolved-args (map #(resolve-references % this model) arguments)
                               real-fn (if-not (seq resolved-args)
                                         resolved
                                         (apply partial (cons resolved resolved-args)))]
                           (println resolved real-fn)
                           (assoc this key (id/draw (real-fn))))))
      "formula" (let [properties (-> fdata :value :properties)]
                  (fn [key this model & more]
                    (let [other (apply hash-map more)
                          properties (current-properties properties (:count other))
                          insert-x (s/replace (:equation properties) #"x" (-> other :x str))
                          equation-replaced (s/replace insert-x #"\s\^\s" " ** ")
                          equation-split (s/split equation-replaced #"\s+")
                          equation-resolved (map #(resolve-references % this model) equation-split)
                          primitives (map str->primitive equation-resolved)]
                      (apply (functionize $=) primitives))))
      "association" (let [field (-> fdata :value :field)
                          master? (:master fdata)]
                      (if master?
                        (fn [key this model & more]
                          (field model))
                        (let [weight (-> fdata :value :select :weight)
                              query-filter (-> fdata :value :select :filter)]
                          (fn [key this model & more]
                            "TO DO")))))))

(defn master-column
  [data]
  (let [[field fdata] (some (fn [[field fdata]]
                              (and (:master fdata)
                                   [field fdata]))
                            data)]
    (when field
      {:key field :fn (field-data fdata)})))

(defn independant-column
  [data deps]
  (let [[field fdata] (some (fn [[field fdata]]
                              (and (empty? (field deps))
                                   [field fdata]))
                            data)]
    (when field
      {:key field :fn (field-data fdata)})))

(defn remove-field-dep
  [deps field]
  (reduce-kv #(assoc %1 %2 (disj %3 field)) {} deps))

(defn build-model-generator
  ([data deps]
   (build-model-generator data deps []))
  ([data deps fn-list]
   (if (empty? data)
     fn-list
     (let [new-item (or (master-column data)
                        (independant-column data deps)
                        (throw (Exception. (str "Circular dependency!" deps))))
           new-data (dissoc data (:key new-item))
           new-deps (remove-field-dep deps (:key new-item))
           new-fn-list (conj fn-list
                             (partial (:fn new-item)
                                      (:key new-item)))]
       (recur new-data new-deps new-fn-list)))))

(defn run-fns
  ([fn-coll model]
   (run-fns fn-coll model {}))
  ([fn-coll model this]
   (if (empty? fn-coll)
     this
     (let [function (first fn-coll)
           new-this (function this model)
           new-fn-coll (rest fn-coll)]
       (recur new-fn-coll model new-this)))))

(defn generators
  [config dependencies]
  (println "Got args")
  config)
