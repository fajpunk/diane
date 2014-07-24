(defproject diane "1.0.0"
  :description "Server-Side Server-Sent-Events client"
  :url "https://github.com/fajpunk/diane"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async  "0.1.303.0-886421-alpha"]
                 [com.taoensso/timbre  "3.2.1"]
                 [clj-http  "0.9.2"]]
  :profiles {:dev {:plugins [[codox "0.8.10"]
                             [lein-expectations "0.0.7"]
                             [lein-autoexpect "1.2.2"]]
                   :dependencies  [[com.ninjudd/eventual  "0.1.0"]
                                   [com.ninjudd/ring-async  "0.2.0"]
                                   [expectations  "2.0.6"]]
                   :source-paths ["test-src" ; For the integration test server
                                  "src"]}})
