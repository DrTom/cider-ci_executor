; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.attachments

  (:use 
    [clojure.stacktrace :as stacktrace]
    [clj-logging-config.log4j :only (set-logger!)]
    )
  (:require 
    [clj-http.client :as http-client]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clojure.data.json :as json]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fs]
    ))

;(set-logger! :level :debug)

(defn put [working-dir attachments url]
  (doseq [[_ {glob :glob content-type :content-type}] attachments]
    (println glob content-type)
    (fs/with-cwd working-dir
      (doseq [file (fs/glob glob)] 
        (try 
          (let [relative (apply str (drop (inc (count working-dir)) (seq (str file))))]
            (logging/debug "sending as attachment: " file)
            (logging/debug "nomalized: " (fs/normalized-path file))
            (logging/debug "name: " (fs/name file))
            (logging/debug "base-name: " (fs/base-name file))
            (logging/debug "relative: " relative)
            (let [result (http-client/put (str url relative)
                                          {:body file  :content-type content-type})]
              (logging/debug "request result: " result)))
          (catch Exception e
            (logging/error e)
            (let [error  (with-out-str (stacktrace/print-stack-trace e))]
              (logging/error  error )
              )))))))


