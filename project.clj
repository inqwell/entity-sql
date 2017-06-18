(defproject entity/entity-sql "0.1.0-SNAPSHOT"
  :description "SQL persistence for use with entity-core"
  :url "https://github.com/inqwell/entity-sql"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[entity/entity-core "0.1.0-SNAPSHOT"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [com.layerware/hugsql "0.4.7"]
                 [hikari-cp "1.7.5"]]
  :plugins [[lein-codox "0.10.3"]]
  :codox {:exclude-vars #"trail-.*|trunc-.*|^(map)?->\p{Upper}"}
  :profiles
  {:dev
   {:dependencies [[com.h2database/h2 "1.4.195"]]}
    :resource-paths ["test/resources"]})