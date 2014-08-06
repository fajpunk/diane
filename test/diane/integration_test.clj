(ns diane.integration-test
  (:require [expectations :refer :all]
            [diane.client :as client]
            [clj-http.conn-mgr :as conn-mgr]
            [test-server :as server]
            [clojure.core.async :as async :refer [chan <!! close! alts!! timeout]]))

(defn url [path]
 (str "http://localhost:" test-server/port path))

(defn subscribe [path]
  (client/subscribe (url path) {:save-request true}))

(defn get-n [n events]
  (doall (repeatedly n #(<!! events))))

(defn add-n-events
  "result is an atom containing a collection.  Take n events off of the events
  channel and add them to result"
  [n events result]
  (swap! result into (get-n n events)))

;; One event
(expect [{:origin (url "/one-event") , :data  "woohoo!", :event  "message", :last-event-id  ""}]

        (let [[events state close] (subscribe "/one-event")
              result (atom [])]
          (add-n-events 1 events result)
          (close)
          @result))

;; Multiple events
(expect [{:origin (url "/multiple-events"), :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin (url "/multiple-events"), :data  "woohoo again!", :event  "message", :last-event-id  ""}]

        (let [[events state close] (subscribe "/multiple-events")
              result (atom [])]
          (add-n-events 2 events result)
          (close)
          @result))

;; Multiple events and reconnect
(expect [{:origin (url "/multiple-events"), :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin (url "/multiple-events"), :data  "woohoo again!", :event  "message", :last-event-id  ""}
         {:origin (url "/multiple-events"), :data  "woohoo!", :event  "message", :last-event-id  ""}]

        (let [[events state close] (subscribe "/multiple-events")
              result (atom [])]
          (add-n-events 2 events result)
          (conn-mgr/shutdown-manager (:conn-mgr @state))
          (add-n-events 1 events result)
          (close)
          @result))

;; Last event id - state
(expect "some-id"
        (let [[events state close] (subscribe "/last-event-id")]
          (get-n 2 events)
          (close)
          (:last-event-id @state)))

;; Last event id - header on reconnect request
(expect "some-id"
        (let [[events state close] (subscribe "/last-event-id")
              captured-response (atom {})]
          (get-n 2 events)
          (conn-mgr/shutdown-manager (:conn-mgr @state))
          (get-n 1 events)
          (reset! captured-response (:response @state))
          (close)
          (get-in @captured-response [:request :headers "Last-Event-ID"])))
          
;; Last event id - appears in event
(expect [{:origin (url "/last-event-id"), :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin (url "/last-event-id"), :data  "woohoo again!", :event  "message", :last-event-id "some-id"}]

        (let [[events state close] (subscribe "/last-event-id")
              result (atom [])]
          (add-n-events 2 events result)
          (close)
          @result))

;; Reconnect time
(expect [{:origin (url "/reconnect-time"), :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin (url "/reconnect-time"), :data  "woohoo again!", :event  "message", :last-event-id  ""}
         {:origin (url "/reconnect-time"), :data  "woohoo!", :event  "message", :last-event-id  ""}]

        (let [[events state close] (subscribe "/reconnect-time")
              result (atom [])]
          (add-n-events 2 events result)
          (conn-mgr/shutdown-manager (:conn-mgr @state))
          (swap! result conj (first (alts!! [events (timeout 1000)])))
          (close)
          @result))

;; Non-200 2xx status
(expect [{:origin (url "/bad-good-status"), :data  "woohoo!", :event  "message", :last-event-id  ""}]

        (let [[events state close] (subscribe "/bad-good-status")
              result (atom [])]
          (add-n-events 1 events result)
          (close)
          @result))

;; Bad content type
(expect :closed

        (let [[events state close] (subscribe "/bad-content-type")
              captured-state (atom nil)]
          (Thread/sleep 500)  ; wait for the response to come back
          (reset! captured-state (:ready-state @state))
          (close)
          @captured-state))
