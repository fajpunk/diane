(ns diane.integration-test
  (:require [expectations :refer :all]
            [diane.client :as client]
            [clj-http.conn-mgr :as conn-mgr]
            [test-server :as server]
            [clojure.core.async :as async :refer [chan <!! close! alts!! timeout]]))

(defn url [path]
 (str "http://localhost:" test-server/port path))

(defn subscribe [path]
  (client/subscribe (url path)))

;; One event
(expect [{:origin (url "/one-event") , :data  "woohoo!", :event  "message", :last-event-id  ""}]

        (let [[events state close] (subscribe "/one-event")
              result (atom [])]
          (swap! result conj (<!! events))
          (close)
          @result))

;; Multiple events
(expect [{:origin (url "/multiple-events"), :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin (url "/multiple-events"), :data  "woohoo again!", :event  "message", :last-event-id  ""}]

        (let [[events state close] (subscribe "/multiple-events")
              result (atom [])]
          (swap! result conj (<!! events))
          (swap! result conj (<!! events))
          (close)
          @result))

;; Multiple events and reconnect
(expect [{:origin (url "/multiple-events"), :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin (url "/multiple-events"), :data  "woohoo again!", :event  "message", :last-event-id  ""}
         {:origin (url "/multiple-events"), :data  "woohoo!", :event  "message", :last-event-id  ""}]

        (let [[events state close] (subscribe "/multiple-events")
              result (atom [])]
          (swap! result conj (<!! events))
          (swap! result conj (<!! events))
          (conn-mgr/shutdown-manager (:conn-mgr @state))
          (swap! result conj (<!! events))
          (close)
          @result))

;; Last id
(expect "some-id"
        (let [[events state close] (subscribe "/last-event-id")]
          (Thread/sleep 500)  ; wait for the response to come back
          (close)
          (:last-event-id @state)))

(expect [{:origin (url "/last-event-id"), :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin (url "/last-event-id"), :data  "woohoo again!", :event  "message", :last-event-id "some-id"}]

        (let [[events state close] (subscribe "/last-event-id")
              result (atom [])]
          (swap! result conj (<!! events))
          (swap! result conj (<!! events))
          (close)
          @result))

;; Reconnect time
(expect [{:origin (url "/reconnect-time"), :data  "woohoo!", :event  "message", :last-event-id  ""}
         {:origin (url "/reconnect-time"), :data  "woohoo again!", :event  "message", :last-event-id  ""}
         {:origin (url "/reconnect-time"), :data  "woohoo!", :event  "message", :last-event-id  ""}]

        (let [[events state close] (subscribe "/reconnect-time")
              result (atom [])]
          (swap! result conj (<!! events))
          (swap! result conj (<!! events))
          (conn-mgr/shutdown-manager (:conn-mgr @state))
          (swap! result conj (first (alts!! [events (timeout 1000)])))
          (close)
          @result))

;; Non-200 2xx status
(expect [{:origin (url "/bad-good-status"), :data  "woohoo!", :event  "message", :last-event-id  ""}]

        (let [[events state close] (subscribe "/bad-good-status")
              result (atom [])]
          (swap! result conj (<!! events))
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

;; FIXME
;; Reconnect with last-event-id set includes correct header
