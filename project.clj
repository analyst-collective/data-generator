(defproject data-generator "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [cheshire "5.5.0"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [hikari-cp "1.6.1"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [org.xerial/sqlite-jdbc "3.8.11.2"]
                 [sqlingvo "0.8.6"]
                 [clj-time "0.11.0"]
                 [faker "0.2.2"]
                 [incanter "1.5.7"]
                 [org.clojure/core.async "0.2.374"]
                 [com.taoensso/timbre "4.3.1"]
                 [org.clojure/math.combinatorics "0.1.1"]]
  :main ^:skip-aot data-generator.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
