(ns diane.client
  (:require [clj-http.client :as client]
            [taoensso.timbre :as timbre]
            [clojure.core.async :as async :refer [chan >!! close!]]
            [clojure.java.io :as io]
            [clojure.string :refer [split]]))
(timbre/refer-timbre)

;; TODO:
;; - Handle unicode chars up to 0x10FFFF
;; - Actually parse as UTF8
;; - Error handling and fault tolerance of any kind
;;   - At least close the gosh darn reader!!
;; - Reconnect
;; - Validate event names
;; - More robust handling of clj-http options
;; - Allow buffer size(/type?) to be passed in
;; - Handle different line endings (Maybe does this already?)
;; - Handle all of the HTTP processing model in section 5 of the spec

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

(defn- set-retry! [retry value]
  (when (re-find #"^\d+$" value)
    (reset! retry (Integer. value))))

(defn- process-field
  "Returns a vector of recur values based on the passed in values and the field
  in the line."
  [line event-name data-buffer last-id retry]
  (let [[field value] (parse-field line)]
    (if (= value "")
      [nil "" last-id]
      (case field
        "event" [value data-buffer last-id]
        "data" [event-name (str data-buffer value "\n") last-id]
        "id" [event-name data-buffer value]
        "retry" (do (set-retry! retry value)
                  [event-name data-buffer last-id])
        [event-name data-buffer last-id]))))

(defn- build-event [origin event-name data-buffer last-id]
  (let [data (strip-trailing-newline data-buffer)]
    {:origin origin
     :data data
     :event (or event-name "message")
     :last-event-id last-id}))
  
(defn- parse-event-stream [stream channel url retry]
  (loop
    [line (.readLine stream)
     event-name nil
     data-buffer "" 
     last-id ""]
    (cond
      (nil? line)  ; The stream is closed
      [line event-name data-buffer last-id]

      (comment? line)
      (recur (.readLine stream) event-name data-buffer last-id)

      (blank-line? line)
      (do
        (when (not= data-buffer "")
          (>!! channel (build-event url event-name data-buffer last-id)))
        (recur (.readLine stream) nil "" last-id))

      :else
      (let [[new-event-name new-data-buffer new-last-id] (process-field line event-name data-buffer last-id retry)]
        (recur (.readLine stream) new-event-name new-data-buffer new-last-id)))))

(defn subscribe
  "Returns a channel onto which will be put Server Side Events from the stream
  obtained by issueing a get request with clj-http to url with options.

  Sticks as close to http://www.w3.org/TR/2009/WD-eventsource-20091029/ as
  makes sense on the server side.
  "
  [url options]
  (let [stream (io/reader (:body (client/get url (assoc options :as :stream))))
        events (chan 25)
        retry (atom 3000)]
    (async/thread
      (parse-event-stream stream events url retry)
      (close! events))
    events))
