(ns diane.parsing-test
  (:require [expectations :refer :all]
            [diane.client :as client]
            [clojure.java.io :as io]
            [clojure.core.async :as async :refer [chan <!! close!]])
  (:import [java.io StringReader]))

(defn make-stream
  "Convert the given string into a BufferedInputReader"
  [string]
  (io/reader (StringReader. string)))

(defn events-for
  "Returns a vector of all of the events that would be put on a channel for
  stream represented by the given string"
  [string]
  (let [stream (make-stream string)
        event-chan (chan 10)]
    (client/parse-event-stream stream event-chan "some-url")
    (close! event-chan)
    (loop [events []
           value (<!! event-chan)]
      (if (nil? value)
        events
        (recur (conj events value) (<!! event-chan))))))

;; An event
(expect [{:origin  "some-url", :data  "Woohoo!", :event  "message", :last-event-id ""}]
        (events-for "data: Woohoo!\n\n"))

;; Two events
(expect [{:origin  "some-url", :data  "Woohoo!", :event  "message", :last-event-id ""}
         {:origin  "some-url", :data  "Woohoo again!", :event  "message", :last-event-id ""}]
        (events-for "data: Woohoo!\n\ndata: Woohoo again!\n\n"))

;; Multiline event
(expect [{:origin  "some-url", :data  "Woohoo!\nWoohoo more!\nand more!", :event  "message", :last-event-id ""}]
        (events-for "data: Woohoo!\ndata: Woohoo more!\ndata: and more!\n\n"))

;; Named event
(expect [{:origin  "some-url", :data  "Woohoo!\nWoohoo more!\nand more!", :event  "my-event", :last-event-id ""}]
        (events-for "event: my-event\ndata: Woohoo!\ndata: Woohoo more!\ndata: and more!\n\n"))

;; Empty event
(expect []
        (events-for "event: my-event\ndata\n\n"))

;; Empty event with colon
(expect []
        (events-for "event: my-event\ndata:\n\n"))

;; A comment
(expect []
        (events-for ":a comment"))

;; Events with ids
(expect [{:origin  "some-url", :data  "Woohoo!", :event  "message", :last-event-id "some-id"}
         {:origin  "some-url", :data  "Woohoo again!", :event  "message", :last-event-id "another-id"}
         {:origin  "some-url", :data  "No id on me.", :event  "message", :last-event-id  "another-id"}]
        (events-for "id: some-id\ndata: Woohoo!\n\nid: another-id\ndata: Woohoo again!\n\ndata: No id on me.\n\n"))