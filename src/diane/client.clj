(ns diane.client
  (:require [clj-http.client :as client]
            [taoensso.timbre :as timbre]
            [clojure.core.async :as async :refer [chan >!!]]
            [clojure.java.io :as io]
            [clojure.string :refer [split]]))
(timbre/refer-timbre)

;; TODO:
;; - Handle unicode chars up to 0x10FFFF
;; - Actually parse as UTF8
;; - Error handling and fault tolerance of any kind
;;   - At least close the gosh darn reader!!
;; - 'retry' fields
;; - Reconnect
;; - Validate event names
;; - Handle lines with no \: char
;; - More robust handling of clj-http options
;; - Allow buffer size(/type?) to be passed in

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
        value (strip-leading-space raw-value)]
    [field value]))

(defn- process-field
  "Returns a vector of recur values based on the passed in values and the field
  in the line."
  [line event-name data-buffer last-id]
  (let [[field value] (parse-field line)]
    (case field
      "event" [value data-buffer last-id]
      "data" [event-name (str data-buffer value "\n")]
      "id" [[event-name data-buffer value]]
      [event-name data-buffer last-id])))

(defn- build-event [origin event-name data-buffer last-id]
  (let [data (strip-trailing-newline data-buffer)]
    {:origin origin
     :data data
     :event (or event-name "message")
     :last-event-id last-id}))
  
(defn subscribe
  "Returns a channel onto which will be put Server Side Events from the stream
  obtained by issueing a get request with clj-http to url with options.

  Sticks as close to http://www.w3.org/TR/2009/WD-eventsource-20091029/ as
  makes sense on the server side.
  "
  [url options]
  (let [reader (io/reader (:body (client/get url (assoc options :as :stream))))
        event-chan (chan 25)]
    (async/thread
      (loop
        [line (.readLine reader)
         event-name nil
         data-buffer "" 
         last-id ""]
        (cond
          (comment? line)
          (recur (.readLine reader) event-name data-buffer last-id)

          (blank-line? line)
          (do
            (when (not= data-buffer "")
              (>!! event-chan (build-event url event-name data-buffer last-id)))
            (recur (.readLine reader) nil "" last-id))

          (field? line) 
          (let [[new-event-name new-data-buffer new-last-id] (process-field line event-name data-buffer last-id)]
            (recur (.readLine reader) new-event-name new-data-buffer new-last-id))

          :else (recur (.readLine reader) event-name data-buffer last-id))))
    event-chan))
