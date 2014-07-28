(ns test-server
  (:require [compojure.core :refer :all]
            [taoensso.timbre :as timbre]
            [org.httpkit.server :as server]))
(timbre/refer-timbre)

(def default-headers
  {"Content-Type" "text/event-stream"})

;; One event
(defn one-event [request]
  (server/with-channel request channel
    (let [response {:headers default-headers
                    :status 200
                    :body "data: woohoo!\n\n"}]
      (server/send! channel response false))))

;; Multiple events
(defn multiple-events [request]
  (server/with-channel request channel
    (let [response {:headers default-headers
                    :status 200
                    :body "data: woohoo!\n\ndata: woohoo again!\n\n"}]
      (server/send! channel response false))))
;; Event, drop connection, more events
;; Non-200 2xx status
;; Wrong Content-Type header

(defroutes test-server-app
  (GET "/one-event" [request] one-event)
  (GET  "/multiple-events"  [request] multiple-events))

(def stop-server-fn (atom nil))

(defn stop []
  (@stop-server-fn))

(defn start []
  (reset! stop-server-fn (server/run-server test-server-app  {:port 9890})))

(defn restart []
  (stop)
  (start))
