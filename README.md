# diane

[![Build Status](https://travis-ci.org/fajpunk/diane.svg?branch=master)](https://travis-ci.org/fajpunk/diane)

Server-Side Server-Sent-Event client

## Why call it diane?

Because the alternative was server-side-server-sent-event-client, or sssse, or
s4e, or some such other terrible name.

## Usage

* [API Docs][]

My use case for writing this was to get events from my Spark Core on the server side
(this was before they added the functionality to call an arbitrary url when an event
 is sent):

```clojure
(require '[clojure.core.async :refer [<!!]])
(require '[diane.client :refer [subscribe]])

(def access-token "xxxxxxxxx")

(let [[events state close] (subscribe "https://api.spark.io/v1/events"
                            {:headers {"Authorization" (str "Bearer " access-token)}})]
  (println (<!! events))
  (println (<!! events))
  (println (<!! events))
  (println "Connection state: " (:ready-state @state))  ;; See client.clj for other state
  :etc
  (close))

## License

Copyright © 2014 Dan Fuchs

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[API docs]: <http://fajpunk.github.io/diane/>
