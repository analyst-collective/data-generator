(ns data-generator.core
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [data-generator.schema :as schema]
            [data-generator.dependency-resolution :as dep]
            [data-generator.generator-factory :as build])
  (:gen-class))

(defn load-config
  [filename]
  (-> filename io/resource slurp (json/parse-string true)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [config (load-config "config.json")]
    (schema/create-tables config)
    (let [dependencies (dep/resolve config)
          _ (clojure.pprint/pprint dependencies)
          generators (build/generators config dependencies)]
      generators)))
