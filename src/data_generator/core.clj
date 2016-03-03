(ns data-generator.core
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [data-generator.db :as db]
            [data-generator.schema :as schema])
  (:gen-class))

(defn load-config
  [filename]
  (-> filename io/resource slurp (json/parse-string true)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [config (load-config "config.json")]
    ;; (clojure.pprint/pprint config)
    (schema/create-tables config)
    ))
