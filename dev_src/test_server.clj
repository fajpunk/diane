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

;; Multiple events
(defn reconnect-time [request]
  (server/with-channel request channel
    (let [response {:headers default-headers
                    :status 200
                    :body "retry: 500\ndata: woohoo!\n\ndata: woohoo again!\n\n"}]
      (server/send! channel response false))))

;; last-event-id
(defn last-event-id [request]
  (server/with-channel request channel
    (let [response {:headers {"Content-Type" "text/event-stream"}
                    :status 200
                    :body "id: some-id\ndata: woohoo!\n\ndata: woohoo again!\n\n"}]
      (server/send! channel response false))))

;; Non-200 2xx status
(def bad-good-count (atom 0))
(defn bad-good-status [request]
  "Return a 204 the first time we connect, a 200 every other time"
  (swap! bad-good-count inc)
  (server/with-channel request channel
    (if (= @bad-good-count 1)
      (let [response {:headers default-headers
                      :status 204
                      :body "data: please\n\ndata: ignore me\n\n"}]
        (server/send! channel response false))
      (let [response {:headers default-headers
                      :status 200
                      :body "data: woohoo!\n\n"}]
        (server/send! channel response false)))))

;; Wrong Content-Type header
(defn bad-content-type [request]
  (server/with-channel request channel
    (let [response {:headers {"Content-Type" "text/something-else"}
                    :status 200
                    :body "data: woohoo!\n\ndata: woohoo again!\n\n"}]
      (server/send! channel response false))))

(defroutes test-server-app
  (GET "/one-event" [request] one-event)
  (GET "/multiple-events"  [request] multiple-events)
  (GET "/reconnect-time"  [request] reconnect-time)
  (GET "/bad-good-status"  [request] bad-good-status)
  (GET "/bad-content-type"  [request] bad-content-type)
  (GET "/last-event-id"  [request] last-event-id))

(def stop-server-fn (atom nil))

(defn stop []
  (@stop-server-fn))

(def port 9890)
(defn start []
  (reset! stop-server-fn (server/run-server test-server-app  {:port port})))

(defn restart []
  (stop)
  (start))
