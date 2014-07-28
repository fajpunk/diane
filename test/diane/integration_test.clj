(ns diane.integration-test
  (:require [expectations :refer :all]
            [diane.client :as client]
            [test-server :as server]
            [clojure.core.async :as async :refer [chan <!! close!]]))

(defmacro with-test-server
  "Start a test server and ensure that it is shut down, no matter what"
  [body]
  `(server/start)
  `(try
     ~@body
     (finally (server/stop))))
