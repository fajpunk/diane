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

;; One event
(expect {:origin  "http://localhost:9890/one-event", :data  "woohoo!", :event  "message", :last-event-id  ""}
       (with-test-server
         (let [[events state close] (client/subscribe (str "http://localhost:" test-server/port "/one-event") {})
               result (<!! events)]
           (close)
           result)))

;; Multiple events
(expect  [{:origin  "http://localhost:9890/multiple-events", :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin  "http://localhost:9890/multiple-events", :data  "woohoo again!", :event  "message", :last-event-id  ""}]
       (with-test-server
         (let [[events state close] (client/subscribe (str "http://localhost:" test-server/port "/multiple-events") {})
               event-vec [(<!! events) (<!! events)]]
           (close)
           event-vec)))
