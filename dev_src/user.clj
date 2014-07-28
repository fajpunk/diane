(ns user
  (:require [diane.client :as client]
            [test-server :refer :all]
            [clojure.core.async :as async :refer [<!!]]
            [taoensso.timbre :as timbre]
            [clojure.tools.namespace.repl :refer [refresh]]))

(timbre/set-level! :trace)

(def url "http://localhost:9890/")

(defn subscribe [path & [options]]
  (let [full-url (str url path)]
    (client/subscribe full-url (or options {}))))
