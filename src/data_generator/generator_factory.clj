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

(defmulti field-data* (fn [value _]
                        (:type value)))

(defmethod field-data* "case"
  [value type-norm]
  (let [result-fns (reduce-kv (fn [m res rvalue]
                                (assoc m res (field-data* rvalue type-norm)))
                              {}
                              (:branches value))]
    (fn [mkey this model & more]
      (let [equation-replaced (s/replace (:check value) #"\s\^\s" " ** ")
            equation-split (s/split equation-replaced #"\s+")
            equation-resolved (map #(resolve-references % this model) equation-split)
            primitives (map str->primitive equation-resolved)
            evaluated (apply (functionize $=) primitives)
            chosen-fn ((keyword evaluated) result-fns)
            args (list* mkey this model more)]
        (apply chosen-fn args)))))

(defmethod field-data* "autoincrement"
  [value type-norm]
  (fn [mkey this model & more]
    this))

(defmethod field-data* "enum"
  [value type-norm]
  (let [weights (:weights value)]
    (fn [mkey this model & more]
      (let [options (mapcat
                     (fn [[k v]]
                       (repeat (resolve-references v this model)
                               (resolve-references (coerce k type-norm) this model)))
                     weights)]
        (assoc this mkey (resolve-references (rand-nth options) this model))))))

(defmethod field-data* "range"
  [value type-norm]
  (let [minimum (or (-> value :properties :min) 0)
        maximum (-> value :properties :max)]
    (cond
      (#{:bigserial :biginteger} type-norm) (fn [mkey this model & more]
                                              (let [maximum (resolve-references maximum this model)
                                                    minimum (resolve-references minimum this model)
                                                    diff (-' maximum minimum)]
                                                (assoc this
                                                       mkey
                                                       (-> diff rand bigint (+' minimum)))))
      (#{:integer :serial} type-norm) (fn [mkey this model & more]
                                        (let [maximum (resolve-references maximum this model)
                                              minimum (resolve-references minimum this model)
                                              diff (-' maximum minimum)]
                                          (assoc this
                                                 mkey
                                                 (-> diff rand int (+ minimum)))))
      (#{:real} type-norm) (fn [mkey this model & more]
                             (let [maximum (resolve-references maximum this model)
                                   minimum (resolve-references minimum this model)
                                   diff (-' maximum minimum)]
                               (assoc this
                                      mkey
                                      (-> diff rand float (+ minimum)))))
      (#{:double} type-norm) (fn [mkey this model & more]
                               (let [maximum (resolve-references maximum this model)
                                     minimum (resolve-references minimum this model)
                                     diff (-' maximum minimum)]
                                 (assoc this
                                        mkey
                                        (-> diff rand (+ minimum))))))))

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
    (fn [mkey this model & more]
      (assoc this
             mkey
             (real-fn)))))

(defmethod field-data* "distribution"
  [value type-norm]
  (let [dist (-> value :properties :type)
        arguments (or (-> value :properties :args) [])
        resolved (resolve (symbol "id" dist))
        real-fn (if-not (seq arguments)
                  resolved
                  (apply partial (cons resolved arguments)))]
    (fn [mkey this model & more]
      (let [resolved-args (map #(resolve-references % this model) arguments)
            real-fn (if-not (seq resolved-args)
                      resolved
                      (apply partial (cons resolved resolved-args)))]
        (println resolved real-fn)
        (assoc this mkey (id/draw (real-fn)))))))

(defmethod field-data* "formula"
  [value type-norm]
  (let [properties (:properties value)]
    (fn [mkey this model & more]
      (let [other (apply hash-map more)
            properties (current-properties properties (:count other))
            insert-x (s/replace (:equation properties) #"x" (-> other :count str))
            equation-replaced (s/replace insert-x #"\s\^\s" " ** ")
            equation-split (s/split equation-replaced #"\s+")
            equation-resolved (map #(resolve-references % this model) equation-split)
            primitives (map str->primitive equation-resolved)]
        (apply (functionize $=) primitives)))))

;; TODO make this a mutli-method to make clearer and allow for extensibility
(defn field-data
  [fdata]
  (let [type-norm (:type-norm fdata)
        association? (-> fdata :type s/lower-case (= "association"))
        val-type (-> fdata :value :type)]
    (if association?
      (let [field (-> fdata :value :field)
                          master? (:master fdata)]
                      (if master?
                        (fn [mkey this model & more]
                          (field model))
                        (let [weight (-> fdata :value :select :weight)
                              query-filter (-> fdata :value :select :filter)]
                          (fn [mkey this model & more]
                            "TO DO"))))
      (field-data* (:value fdata) type-norm))))

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
  [deps remove-field]
  (reduce-kv (fn [m model deps-map]
               (let [field-deps (:field-deps deps-map)
                     new-field-deps (reduce-kv (fn [m1 field fdeps]
                                                 (assoc m1 field (disj fdeps remove-field)))
                                               field-deps
                                               field-deps)]
                 (assoc m :field-deps new-field-deps)))
             deps
             deps))

(defn build-model-generator
  ([data deps]
   (build-model-generator (:model data) deps []))
  ([mdata deps fn-list]
   (if (empty? mdata)
     fn-list
     (let [new-item (or (master-column mdata)
                        (independant-column mdata deps)
                        (throw (Exception. (str "Circular dependency!" deps))))
           new-mdata (dissoc mdata (:key new-item))
           new-deps (remove-field-dep deps (:key new-item))
           new-fn-list (conj fn-list
                             (partial (:fn new-item)
                                      (:key new-item)))]
       (recur new-mdata new-deps new-fn-list)))))

(defn add-generators
  [config dependencies]
  (let [models (:models config)
        new-models (reduce-kv (fn [m table data]
                                (let [model (:model data)
                                      fn-list (build-model-generator model dependencies)]
                                  (assoc m table :fn-list fn-list)))
                              models
                              models)]
    (assoc config :models new-models)))

(defn generators
  [config dependencies]
  (println "Got args")
  config)
