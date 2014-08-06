(ns test-server
  (:require [compojure.core :refer :all]
            [taoensso.timbre :as timbre]
            [org.httpkit.server :as server]))
(timbre/refer-timbre)

(def default-headers
  {"Content-Type" "text/event-stream"})

(def bad-good-count (atom 0))

(defroutes test-server-app
  (GET "/one-event" request 
       (server/with-channel request channel
         (let [response {:headers default-headers
                         :status 200
                         :body "data: woohoo!\n\n"}]
           (server/send! channel response false))))

  (GET "/multiple-events"  request 
       (server/with-channel request channel
         (let [response {:headers default-headers
                         :status 200
                         :body "data: woohoo!\n\ndata: woohoo again!\n\n"}]
           (server/send! channel response false))))

  (GET "/reconnect-time"  request 
       (server/with-channel request channel
         (let [response {:headers default-headers
                         :status 200
                         :body "retry: 500\ndata: woohoo!\n\ndata: woohoo again!\n\n"}]
           (server/send! channel response false))))

  (GET "/bad-good-status"  request 
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

  (GET "/bad-content-type"  request 
       (server/with-channel request channel
         (let [response {:headers {"Content-Type" "text/something-else"}
                         :status 200
                         :body "data: woohoo!\n\ndata: woohoo again!\n\n"}]
           (server/send! channel response false))))

  (GET "/last-event-id"  request 
       (server/with-channel request channel
         (let [response {:headers {"Content-Type" "text/event-stream"}
                         :status 200
                         :body "data: woohoo!\n\nid: some-id\ndata: woohoo again!\n\n"}]
           (server/send! channel response false)))))

(def stop-server-fn (atom nil))

(defn stop []
  (@stop-server-fn))

(def port 9890)

(defn start []
  (reset! stop-server-fn (server/run-server test-server-app  {:port port})))

(defn restart []
  (stop)
  (start))
