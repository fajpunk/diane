(ns diane.client
  (:require [clj-http.client :as client]
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
;; - Handle all of the HTTP processing model in section 5 of the spec
;;   - Allow client to be closed
;; - Indicate state of connection
;; - Redirects exactly according to the spec
;;   - just automatically follows them for now, but will always request
;;     the original URL on reconnect

(defn- comment? [line]
  (= (first line) \:))

(defn- blank-line? [line]
  (= line ""))

(defn- field? [line]
  (.contains line ":"))

(defn- strip-leading-space [value]
  (last (split value #"^ ")))

(defn- strip-trailing-newline [value]
  (first (split value #"\n$")))

(defn- parse-field [line]
  (let [[field raw-value] (split line #":" 2)
        safe-value (or raw-value "")
        value (strip-leading-space safe-value)]
    [field value]))

(defn- set-reconnection-time! [client-state value]
  (when (re-find #"^\d+$" value)
    (swap! client-state assoc :reconnection-time (Integer. value))))

(defn- process-field
  "Returns a vector of recur values based on the passed in values and the field
  in the line."
  [line event-name data-buffer client-state]
  (let [[field value] (parse-field line)]
    (if (= value "")
      [nil ""]
      (case field
        "event" [value data-buffer]
        "data" [event-name (str data-buffer value "\n")]
        "id" (do 
               (swap! client-state assoc :last-event-id value)
               [event-name data-buffer])
        "retry" (do (set-reconnection-time! client-state value)
                  [event-name data-buffer])
        [event-name data-buffer]))))

(defn- build-event [origin event-name data-buffer client-state]
  (let [data (strip-trailing-newline data-buffer)]
    {:origin origin
     :data data
     :event (or event-name "message")
     :last-event-id (:last-event-id @client-state)}))
  
(defn- parse-event-stream [stream channel url client-state]
  (loop
    [line (.readLine stream)
     event-name nil
     data-buffer ""]
    (cond
      (nil? line)  ; The stream is closed
      [line event-name data-buffer]

      (comment? line)
      (recur (.readLine stream) event-name data-buffer)

      (blank-line? line)
      (do
        (when (not= data-buffer "")
          (>!! channel (build-event url event-name data-buffer client-state)))
        (recur (.readLine stream) nil ""))

      :else
      (let [[new-event-name new-data-buffer] (process-field line event-name data-buffer client-state)]
        (recur (.readLine stream) new-event-name new-data-buffer)))))

(defn- ok-status? [status]
  (= \2 (first (str status))))

(defn- valid-content-type? [headers]
  (= "text/event-stream" (get headers "Content-Type")))

(def ready-state-map {:connecting 0
                      :open 1
                      :closed 2})

(defn- wait-for-reconnect! [client-state]
  (tracef "Reconnecting after %s milliseconds..." (:reconnection-time @client-state))
  (swap! client-state assoc :ready-state (:connecting ready-state-map))
  (Thread/sleep (:reconnection-time @client-state)))

(defn- reconnect-options [options client-state]
  (let [last-event-id (:last-event-id @client-state)]
    (if (empty? last-event-id)
      options
      (assoc-in options [:headers "Last-Event-ID"] (:last-event-id @client-state)))))

(defn subscribe
  "Returns a channel onto which will be put Server Side Events from the stream
  obtained by issueing a get request with clj-http to url with options.

  Sticks as close to http://www.w3.org/TR/2009/WD-eventsource-20091029/ as
  makes sense on the server side.
  "
  [url options]
  (let [default-options {:as :stream
                         :headers {"Cache-Control" "no-cache"}}
        all-options (merge default-options options)
        events (chan 25)
        client-state (atom {:ready-state (:connecting ready-state-map)
                            :last-event-id ""
                            :reconnection-time 3000})]
    (async/thread
      (loop [{:keys [status body headers]} (client/get url all-options)]
        (tracef "Connected to %s" url)
        (cond 
          (and (= 200 status) (valid-content-type? headers))
          (do 
            (with-open [stream (io/reader body)]
              (parse-event-stream stream events url client-state))
            (wait-for-reconnect! client-state)
            (recur (client/get url (reconnect-options all-options client-state))))

          (and (ok-status? status) (valid-content-type? headers))
          (do
            (wait-for-reconnect! client-state)
            (recur (client/get url (reconnect-options all-options client-state))))

          :else nil)))
    events))
