; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 
;
(ns cider-ci.main
  (:gen-class)
  (:import 
    [java.io File]
    )
  (:require 
    [clojure.tools.logging :as logging]
    [cider-ci.nrepl :as nrepl]
    [cider-ci.reporter :as reporter]
    [cider-ci.shared :as shared]
    [cider-ci.trial :as trial]
    [cider-ci.exec :as exec]
    [cider-ci.util :as util]
    [cider-ci.web :as web]
    )
  (:use 
    [clj-logging-config.log4j :only (set-logger!)]
    ))

;(set-logger! :level :debug)

(defn read-config []
  (logging/info "read-config invoked")
  (util/try-read-and-apply-config 
    {:shared shared/conf 
     :nrepl nrepl/conf
     :reporter reporter/conf
     :web web/conf
     :execution exec/conf
     } 
    "/etc/cider-ci/conf"
    "/etc/cider-ci_conf"
    (str (System/getProperty "user.home") (File/separator) "cider-ci_conf")
    "cider-ci_conf"))

(defn -main
  [& args]
  (logging/info "starting -main " args)
  (read-config)
  (shared/initialize)
  (trial/initialize)
  (nrepl/start-server)
  (web/start-server))

