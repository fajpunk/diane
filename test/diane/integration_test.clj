(ns diane.integration-test
  (:require [expectations :refer :all]
            [diane.client :as client]
            [test-server :as server]
            [clojure.core.async :as async :refer [chan <!! close!]]))

(defmacro with-test-server
  "Start a test server and ensure that it is shut down, no matter what"
  [& body]
  `(do 
     (server/start)
     (try
       (do ~@body)
       (finally (server/stop)))))

(defn get-events
  "Subscribe to an event stream at the test server path and return a vector
  with n events from the stream"
  [n path]
  (with-test-server
    (let [[events state close] (client/subscribe (str "http://localhost:" test-server/port path) {})
          n-results (async/take n events)
          all-results (<!! (async/into [] n-results))]
      (close)
      all-results)))

;; One event
(expect [{:origin  "http://localhost:9890/one-event", :data  "woohoo!", :event  "message", :last-event-id  ""}]
        (get-events 1 "/one-event"))

;; Multiple events
(expect [{:origin  "http://localhost:9890/multiple-events", :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin  "http://localhost:9890/multiple-events", :data  "woohoo again!", :event  "message", :last-event-id  ""}]
        (get-events 2 "/multiple-events"))
