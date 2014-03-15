; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 
 
(ns cider-ci.shared
  (:require
    [clojure.tools.logging :as logging]
    )
  (:import 
    [java.io File]
    )
  (:use 
    [clj-logging-config.log4j :only (set-logger!)]
    ))

;(set-logger! :level :debug)

(defonce conf (atom {:working-dir (str (System/getProperty "user.home") (File/separator) "cider-ci_working-dir")
                     :git-repos-dir (str (System/getProperty "user.home") (File/separator) "cider-ci_git-repos-dir" )
                     }))

(defn initialize []
  (.mkdir (File. (:working-dir @conf)))
  (.mkdir (File. (:git-repos-dir @conf))))

