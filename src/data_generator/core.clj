(ns data-generator.core
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [data-generator.schema :as schema]
            [clojure.string :as s]
            [incanter.distributions :as id] ;; functions are resovled in this namespace
            [incanter.core :refer [$=]] ;; macro is resolved at runtime in this namespace
            [data-generator.config :as conf]
            [data-generator.dependency-resolution :as dep]
            [data-generator.generator-factory :as build]
            [data-generator.generator :as generator])
  (:gen-class))

(defn generate-data
  [config]
  (let [dependencies (dep/resolve-deps config)
        _ (schema/create-tables config)
        with-generators (build/generators config dependencies)]
    (generator/generate with-generators dependencies)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [config (-> "config.json"
                   conf/load-json
                   conf/association-field-transfer
                   conf/normalize-models)]
    (generate-data config)))
