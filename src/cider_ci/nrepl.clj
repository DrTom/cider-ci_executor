; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.nrepl 
  (:require 
    [cider-ci.util :as util]
    [clojure.tools.logging :as logging]
    [clojure.tools.nrepl.server :as nrepl-server]
    )
  (:use 
    [clj-logging-config.log4j :only (set-logger!)]
    ))

;(set-logger! :level :debug)

(defonce conf (atom {:port 7888
                     :bind "127.0.0.1"
                     :enabled false
                     }))

(defonce server nil)

(defn stop-server []
  (logging/info "stopping server")
  (nrepl-server/stop-server server)
  (def server nil))

(defn start-server []
  (if server (stop-server))
  (if (:enabled @conf)
    (let [args (flatten (seq (select-keys @conf [:port :bind])))]
      (do 
        (logging/info "starting server " (with-out-str (clojure.pprint/pprint args)))
        (def server (apply nrepl-server/start-server args ))))))

