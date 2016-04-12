(defproject data-generator "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :resource-paths ["resources"]
  :dependencies [[cheshire "5.5.0"]
                 [clj-time "0.11.0"]
                 [com.taoensso/timbre "4.3.1"]
                 [faker "0.2.2"]
                 [hikari-cp "1.6.1"]
                 [incanter "1.5.7"]
                 [log4j/log4j "1.2.17"
                  :exclusions [javax.mail/mail
                               javax.jms/jms
                               com.sun.jdmk/jmxtools
                               com.sun.jmx/jmxri]]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [org.clojure/math.combinatorics "0.1.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [org.slf4j/slf4j-log4j12 "1.7.13"]
                 [org.xerial/sqlite-jdbc "3.8.11.2"]
                 [sqlingvo "0.8.6"]]
  :main ^:skip-aot data-generator.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
