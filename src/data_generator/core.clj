(ns data-generator.core
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [data-generator.config :as conf]
            [data-generator.dependency-resolution :as dep]
            [data-generator.generator-factory :as build]
            [data-generator.generator :as generator]
            [data-generator.storage :as storage]
            [data-generator.storage.sql]
            [data-generator.storage.postgresql])
  (:gen-class))

(defn generate-data
  [config]
  (require '[clj-time.core :as t])
  (require '[incanter.core :refer [$=]]) ; Dynamically resolved symbols require these references
  (require '[incanter.distributions :as id]) 
  (let [config-prepped (-> config
                           conf/association-field-transfer
                           conf/normalize-models
                           storage/normalize-storage-type
                           storage/storage-prep)
        dependencies (dep/resolve-deps config-prepped)]
    (storage/create-tables config-prepped)
    (log/info "DEPENDENCIES" dependencies)
    (log/info "PREPPED CONFIG" config-prepped)
    (-> config-prepped
        (build/generators dependencies)
        (generator/generate dependencies)
        storage/drop-virtual-columns)))

(defn -main
  "JAR entry point, accepts name of packaged config or file path to external config"
  [& args]
  (let [start (System/currentTimeMillis)
        file-name (or (-> args first) "config.json")
        config (conf/load-json file-name)]
    (if-not config
      (log/info file-name "not found. Exiting without running. Please supply a valid path to a config file.")
      (generate-data config))
    (log/info "Completed running in" (- (System/currentTimeMillis) start) "milliseconds.")))
