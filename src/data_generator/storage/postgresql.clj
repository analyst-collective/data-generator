(ns data-generator.storage.postgresql
  (:require [clojure.java.jdbc :as j]
            [sqlingvo.core :as sql]
            [sqlingvo.db :refer [postgresql]]))

(def pg (postgresql))
