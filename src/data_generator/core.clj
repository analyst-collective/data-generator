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
  (println "here")
  (let [config-prepped (-> config
                           conf/association-field-transfer
                           conf/normalize-models)
        dependencies (dep/resolve-deps config-prepped)
        _ (schema/create-tables config-prepped)
        _ (println "DEPENDENCIES" dependencies)]
    (println "Got here")
    (-> config-prepped
        (build/generators dependencies)
        (generator/generate dependencies)
        schema/drop-virtual-columns)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [file-name (or (-> args first) "config.json")
        config (conf/load-json file-name)]
    (if-not config
      (println file-name "not found. Exiting without running. Please supply a valid path to a config file.")
      (generate-data config))))
