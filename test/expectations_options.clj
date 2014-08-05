(ns expectations-options
  (:require expectations
            [test-server :as server]))

(defn start-integration-test-server 
  {:expectations-options :before-run}
  []
  (server/start)
  (println "Test server started on port" server/port))

(defn stop-integration-test-server 
  {:expectations-options :after-run}
  []
  (server/stop)
  (println "Test server stopped"))
