; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.exec
  (:import 
    [java.io File]
    [java.util UUID]
    [org.apache.commons.exec ExecuteWatchdog]
    )
  (:require
    [clj-commons-exec :as commons-exec]
    [clj-time.core :as time]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    [cider-ci.util :as util]
    )
  (:use 
    [clj-logging-config.log4j :only (set-logger!)]
    [clojure.java.shell :as shell]
    [clojure.stacktrace :only (print-stack-trace)]
    ))

;(set-logger! :level :debug)
;(clojure.pprint/pprint @(commons-exec/sh ["bash" "-l" "-c" "load_rbenv && rbenv shell ruby-2.0.0 && ruby -v"] {:env (System/getenv) }))
;(clojure.pprint/pprint @(commons-exec/sh ["sh" "-l" "-c" "bash -l -c \"env |sort\""] {:env {}}))
;(shell/sh "bash" "-l" "-c" "env" :env {})


(def conf (atom {:environment_variables {}}))


(def defaul-system-interpreter
  (condp = (clojure.string/lower-case (System/getProperty "os.name"))
    "windows" ["cmd.exe" "/c"]
    ["bash" "-l"]))

(defn prepare-script-file [script]
  (let [script-file (File/createTempFile "cider-ci_", ".script")]
    (.deleteOnExit script-file)
    (spit script-file script)
    (.setExecutable script-file true)
    script-file))

(defn ^:private prepare-env-variables [{ex-uuid :cider_ci_execution_id 
                                        trial-uuid :cider_ci_trial_id 
                                        task-uuid :cider_ci_task_id :as params}]
  ; TODO pull-up the complete params here; there is some duplication 
  (logging/debug "prepare-env-variables :cider-ci_execution_id " ex-uuid ":cider-ci_trial_id " trial-uuid " params: " params)
  (let [res (util/upper-case-keys 
              (util/rubyize-keys
                 (conj params 
                       (:environment_variables @conf)
                       { })))]
                 (logging/debug "prepare-env-variables res: " res)
    res))

(defn exec-script-for-params [params]

  (logging/info (str "exec-script-for-params" (select-keys params [:name])))
  (logging/debug "exec-script-for-params params:" params)
  (try
    (let [started {:started_at (time/now)}
          working-dir (:working_dir params)
          env-variables (prepare-env-variables (conj {:cider-ci_working_dir working-dir} 
                                                     (or (:ports params) {}) 
                                                     (:environment_variables params)))
          timeout (or (:timeout params) 200)
          interpreter (or (:interpreter params) defaul-system-interpreter)
          script-file (prepare-script-file (:body params))  
          command (conj interpreter (.getAbsolutePath script-file))
          exec-res (deref (commons-exec/sh command 
                                           {:env (conj {} (System/getenv) env-variables)
                                            :dir working-dir  
                                            :watchdog (* 1000 timeout)}))]
      (conj params 
            started 
            {:finished_at (time/now)
             :exit_status (:exit exec-res)
             :state (condp = (:exit exec-res) 
                      0 "success" 
                      "failed")
             :stdout (:out exec-res)
             :stderr (:err exec-res) 
             :error (:error exec-res)
             :interpreter interpreter
             }))
    (catch Exception e
      (do
        (logging/error (with-out-str (print-stack-trace e)))
        (conj params
              {:state "failed"
               :error (with-out-str (print-stack-trace e))
               })))))

(defn start-service-process [params]
  (try
    (let [started {:started_at (time/now)}
          working-dir (:working_dir params)
          env-variables (prepare-env-variables 
                          (conj {:cider-ci_working_dir working-dir} 
                                (or (:ports params) {}) 
                                (:environment_variables params)))
          timeout (or (when-let [s (:timeout params)] (* 1000 s))  ExecuteWatchdog/INFINITE_TIMEOUT)
          watchdog (ExecuteWatchdog. timeout) 
          interpreter (or (:interpreter params) defaul-system-interpreter)
          script-file (prepare-script-file (:body params))  
          command (conj interpreter (.getAbsolutePath script-file))
          exec-promise (commons-exec/sh command 
                                        {:env (conj {} (System/getenv) env-variables)
                                         :dir working-dir  
                                         :watchdog watchdog})]
      (conj params 
            started 
            {:finished_at (time/now)
             :exit_status 0 
             :state "success" 
             :stdout "" 
             :stderr "" 
             :error ""
             :interpreter interpreter }
            {:watchdog watchdog
             :exec_promise exec-promise
             }))
    (catch Exception e
      (logging/error (with-out-str (print-stack-trace e)))
      (conj params
            {:state "failed"
             :error (with-out-str (print-stack-trace e))
             }))))

(defn stop-service [params]
  (.destroyProcess (:watchdog params))
  (let [exec-res (deref (:exec_promise params))]
    (conj params 
          {:finished_at (time/now)
           :exit_status (:exit exec-res)
           :stdout (:out exec-res)
           :stderr (:err exec-res) 
           :error (:error exec-res)
           })))


