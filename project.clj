(defproject diane "1.0.0"
  :description "Server-Side Server-Sent-Events client"
  :url "https://github.com/fajpunk/diane"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [com.taoensso/timbre "3.2.1"]
                 [clj-http "0.9.2"]]
  :source-paths ["src"]
  :profiles {:dev {:plugins [[codox "0.8.10"]
                             [speclj "3.0.0"]]
                   :dependencies  [[http-kit "2.1.18"]
                                   [speclj "3.0.0"]
                                   [compojure  "1.1.8"]
                                   [org.clojure/tools.namespace  "0.2.5"]]
                   :source-paths ["dev_src"]
                   :codox {:sources ["src"]}}})
