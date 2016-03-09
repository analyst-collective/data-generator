(ns data-generator.core
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [data-generator.schema :as schema]
            [clojure.string :as s]
            [data-generator.config :as conf]
            [data-generator.dependency-resolution :as dep]
            [data-generator.generator-factory :as build])
  (:gen-class))

(defn generate-data
  [config]
  (let [dependencies (dep/resolve config)]
    (clojure.pprint/pprint dependencies)
    (schema/create-tables config)
    (build/generators config dependencies)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [config (-> "config.json"
                   conf/load-json
                   conf/association-field-transfer
                   conf/normalize-models)]
    (generate-data config)))
