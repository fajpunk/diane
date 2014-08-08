(ns diane.client
  (:require [clj-http.client :as client]
            [clj-http.conn-mgr :as conn-mgr]
            [taoensso.timbre :as timbre]
            [clojure.core.async :as async :refer [chan >!! close!]]
            [clojure.java.io :as io]
            [clojure.string :refer [split]]))
(timbre/refer-timbre)

;; TODO:
;; - Handle unicode chars up to 0x10FFFF
;; - Error handling and fault tolerance of any kind
;;   - At least close the gosh darn reader!!
;; - Validate event names
;; - Allow buffer size(/type?) to be passed in
;; - Handle different line endings (Maybe does this already?)
;; - Redirects exactly according to the spec
;;   - just automatically follows them for now, but will always request
;;     the original URL on reconnect

;; Parsing stuff
(defn- comment? [line]
  (= (first line) \:))

(defn- field? [line]
  (.contains line ":"))

(defn- strip-leading-space [value]
  (last (split value #"^ ")))

(defn- strip-trailing-newline [value]
  (or (first (split value #"\n$")) ""))

(defn- parse-field [line]
  (let [[field raw-value] (split line #":" 2)
        safe-value (or raw-value "")
        value (strip-leading-space safe-value)]
    [field value]))

(defn- set-reconnection-time! [state value]
  (when (re-find #"^\d+$" value)
    (swap! state assoc :reconnection-time (Integer. value))))

(defn- process-field
  "Returns a vector of recur values based on the passed in values and the field
  in the line."
  [line event-name data-buffer state]
  (let [[field value] (parse-field line)]
    (case field
      "event" [value data-buffer]
      "data" [event-name (str data-buffer value "\n")]
      "id" (do 
             (swap! state assoc :last-event-id value)
             [event-name data-buffer])
      "retry" (do (set-reconnection-time! state value)
                  [event-name data-buffer])
      [event-name data-buffer])))

(defn- build-event [origin event-name data-buffer state]
  (let [data (strip-trailing-newline data-buffer)]
    {:origin origin
     :data data
     :event (if (empty? event-name) "message" event-name)
     :last-event-id (:last-event-id @state)}))
  
(defn- parse-event-stream [stream channel url state]
  (loop
    [line (.readLine stream)
     event-name ""
     data-buffer ""]
    (cond
      (nil? line)  ; The stream is closed
      [line event-name data-buffer]

      (comment? line)
      (recur (.readLine stream) event-name data-buffer)

      (empty? line)
      (do
        (when (not= data-buffer "")
          (>!! channel (build-event url event-name data-buffer state)))
        (recur (.readLine stream) "" ""))

      :else
      (let [[new-event-name new-data-buffer] (process-field line event-name data-buffer state)]
        (recur (.readLine stream) new-event-name new-data-buffer)))))

(defn- ok-status? [status]
  (= \2 (first (str status))))

(defn- valid-content-type? [headers]
  (= "text/event-stream" (get headers "Content-Type")))

(defn- reconnect-options [initial-options state]
  (let [options (assoc initial-options :connection-manager (:conn-mgr state))
        last-event-id (:last-event-id @state)]
    (if (empty? last-event-id)
      options
      (assoc-in options [:headers "Last-Event-ID"] last-event-id))))

;; HTTP stuff
(defn- make-close-fn
  "Return a function that:
    - Releases the http-connection
    - Closes the events channel
    - Sets the connection state to closed"
  [channel state]
  (fn []
    (swap! state assoc :ready-state :closed)
    (close! channel)
    (conn-mgr/shutdown-manager (:conn-mgr @state))))

(defn- wait-for-reconnect! [state]
  (let [reconnection-time (:reconnection-time @state)]
    (tracef "Reconnecting after %s milliseconds..." reconnection-time)
    (swap! state assoc :ready-state :connecting)
    (Thread/sleep reconnection-time)))

(defn- reconnect-if-not-closed [url options state]
  (if (not= :closed (:ready-state @state))
    (do
      (wait-for-reconnect! state)
      (swap! state assoc :conn-mgr (conn-mgr/make-regular-conn-manager {}))
      (client/get url (reconnect-options options state)))
    {}))

(defn subscribe
  "Returns:
    [events-channel state-atom close-fn]
    - a channel onto which will be put Server Sent Events from the stream
      obtained by issuing a get request with clj-http to url with 
      custom-options
    - an atom representing the state of the client
    - and function with which to close the client

  Sticks as close to http://www.w3.org/TR/2009/WD-eventsource-20091029/ as
  makes sense on the server side.
  "
  [url & [custom-options]]
  (let [default-options {:as :stream
                         :headers {"Cache-Control" "no-cache"}}
        options (merge default-options 
                       custom-options
                       {:headers (merge (:headers default-options) (:headers custom-options))})
        events (chan)
        state (atom {:ready-state :connecting
                            :last-event-id ""
                            :reconnection-time 3000
                            :response {}
                            :conn-mgr (conn-mgr/make-regular-conn-manager {})})
        close-fn! (make-close-fn events state)]
    (async/thread
      (loop [{:keys [status body headers] :as response} (client/get url (assoc options :connection-manager (:conn-mgr @state)))]
        (swap! state assoc :response response)
        (cond 
          (= (:ready-state @state) :closed)
          nil

          (and (= 200 status) (valid-content-type? headers))
          (do 
            (swap! state assoc :ready-state :open)
            (with-open [stream (io/reader body)]
              (parse-event-stream stream events url state))
            (recur (reconnect-if-not-closed url options state)))

          (and (ok-status? status) (valid-content-type? headers))
          (recur (reconnect-if-not-closed url options state))

          :else (close-fn!))))
    [events state close-fn!]))
