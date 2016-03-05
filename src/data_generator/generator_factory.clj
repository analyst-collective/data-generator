(ns data-generator.generator-factory
  (:require [clj-time.coerce :as c]))

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

(defn field-data
  [fdata]
  (let [type (:type-norm fdata)
        val-type (-> fdata :value :type)]
    (case val-type
      "enum" (let [options (mapcat
                            (fn [[k v]]
                              (repeat v (coerce k type)))
                            (-> fdata :value :weights)
                            )]
               (println options)
               (fn [] (rand-nth options)))
      "range" (let [minimum (or (-> fdata :value :properties :min) 0)
                    maximum (-> fdata :value :properties :max)
                    diff (-' maximum minimum)]
                (println type val-type minimum maximum diff)
                (cond
                  (#{:bigserial :biginteger} type) #(-> diff rand bigint (+' minimum))
                  (#{:integer :serial} type) #(-> diff rand int (+ minimum))
                  (#{:real} type) #(-> diff rand float (+ minimum))
                  (#{:double} type) #(-> diff rand (+ minimum)))))))


(defn generators
  [config dependencies]
  (println "Got args"))
