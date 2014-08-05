(ns diane.integration-test
  (:require [expectations :refer :all]
            [diane.client :as client]
            [clj-http.conn-mgr :as conn-mgr]
            [test-server :as server]
            [clojure.core.async :as async :refer [chan <!! close! alts!! timeout]]))

(defn get-events
  "Subscribe to an event stream at the test server path and return a vector
  with n events from the stream"
  [n path]
  (let [[events state close] (client/subscribe (str "http://localhost:" test-server/port path) {})
        n-results (async/take n events)
        all-results (<!! (async/into [] n-results))]
    (close)
    all-results))

;; One event
(expect [{:origin  "http://localhost:9890/one-event", :data  "woohoo!", :event  "message", :last-event-id  ""}]
        (get-events 1 "/one-event"))

;; Multiple events
(expect [{:origin  "http://localhost:9890/multiple-events", :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin  "http://localhost:9890/multiple-events", :data  "woohoo again!", :event  "message", :last-event-id  ""}]
        (get-events 2 "/multiple-events"))

;; Multiple events and reconnect
(expect [{:origin  "http://localhost:9890/multiple-events", :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin  "http://localhost:9890/multiple-events", :data  "woohoo again!", :event  "message", :last-event-id  ""}
         {:origin  "http://localhost:9890/multiple-events", :data  "woohoo!", :event  "message", :last-event-id  ""}]

        (let [[events state close] (client/subscribe (str "http://localhost:" test-server/port "/multiple-events") {})
              result (atom [])]
          (swap! result conj (<!! events))
          (swap! result conj (<!! events))
          (conn-mgr/shutdown-manager (:conn-mgr @state))
          (swap! result conj (<!! events))
          (close)
          @result))

;; Last id
(expect "some-id"
        (let [[events state close] (client/subscribe (str "http://localhost:" test-server/port "/last-event-id") {})]
          (Thread/sleep 500)  ; wait for the response to come back
          (close)
          (:last-event-id @state)))

;; Reconnect time
(expect [{:origin  "http://localhost:9890/reconnect-time", :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin  "http://localhost:9890/reconnect-time", :data  "woohoo again!", :event  "message", :last-event-id  ""}
         {:origin  "http://localhost:9890/reconnect-time", :data  "woohoo!", :event  "message", :last-event-id  ""}]

        (let [[events state close] (client/subscribe (str "http://localhost:" test-server/port "/reconnect-time") {})
              result (atom [])]
          (swap! result conj (<!! events))
          (swap! result conj (<!! events))
          (conn-mgr/shutdown-manager (:conn-mgr @state))
          (swap! result conj (first (alts!! [events (timeout 1000)])))
          (close)
          @result))

;; Non-200 2xx status
(expect [{:origin  "http://localhost:9890/bad-good-status", :data  "woohoo!", :event  "message", :last-event-id  ""}]
        (let [[events state close] (client/subscribe (str "http://localhost:" test-server/port "/bad-good-status") {})
              result (atom [])]
          (swap! result conj (<!! events))
          (close)
          @result))

;; Bad content type
(expect :closed
        (let [[events state close] (client/subscribe (str "http://localhost:" test-server/port "/bad-content-type") {})
              captured-state (atom nil)]
          (Thread/sleep 500)  ; wait for the response to come back
          (reset! captured-state (:ready-state @state))
          (close)
          @captured-state))
