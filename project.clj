(defproject data-generator "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [cheshire "5.5.0"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [sqlingvo "0.8.6"]]
  :main ^:skip-aot data-generator.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
