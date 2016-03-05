(ns data-generator.core
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [data-generator.schema :as schema]
            [clojure.string :as s]
            [data-generator.dependency-resolution :as dep]
            [data-generator.generator-factory :as build])
  (:gen-class))

(defn load-config
  [filename]
  (-> filename io/resource slurp (json/parse-string true)))

(defn normalize-fields
  [[table data]]
  (let [model (:model data)]
    [table {:model (apply
                    hash-map
                    (mapcat (fn [[field fdata]]
                              (let [type (-> fdata :type s/lower-case)
                                    value (:value fdata)
                                    autoincrement? (when value
                                                     (-> value :type s/lower-case (= "autoincrement")))
                                    type-norm (cond
                                                (#{"int" "integer"} type) (if autoincrement?
                                                                            :serial
                                                                            :integer)
                                                (#{"string" "text"} type) :text
                                                (re-find  #"^(var)(char)?([\s]*)?(\(([\d]*)\))?$" type) :text
                                                (#{"real" "float"} type) :real
                                                (#{"double"} type) :double
                                                (#{"bigint" "biginteger"} type) (if autoincrement?
                                                                                  :bigserial
                                                                                  :biginteger)
                                                (#{"date"} type) :date
                                                (#{"datetime" "timestamp" "timestamp with timezone"} type) :timestamp-with-time-zone
                                                (#{"bool" "boolean"} type) :boolean
                                        ; Throw error?
                                                :default "UNKNOWN")]
                                [field (assoc fdata :type-norm type-norm)]))
                            model))}]))

(defn normalize-models
  [config]
  (let [models (:models config)
        fixed (apply hash-map (mapcat normalize-fields models))]
    (assoc config :models fixed)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [config (load-config "config.json")
        normalized (normalize-models config)]
    (schema/create-tables normalized)
    (let [dependencies (dep/resolve normalized)
          ;; dependencies (dep/resolve config)
          _ (clojure.pprint/pprint dependencies)
          generators (build/generators normalized dependencies)]
      generators)))
