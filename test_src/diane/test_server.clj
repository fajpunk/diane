(ns diane.test-server
  (:require [clojure.core.async :refer [go >! chan close!]]
            [compojure.core :refer :all]
            [ninjudd.eventual.server :refer [edn-events]]
            [ring.adapter.jetty-async :refer [run-jetty-async]]))

(defn one-event [request]
  (let [events (chan)
        event {:some "event"}]
    (go 
      (>! events event)
      (close! events))
    (edn-events events)))

(defroutes test-sse-server
  (GET "/one-event" request (one-event request)))

(defn start-test-server []
  (run-jetty-async test-sse-server {:join? false :port 8987}))
