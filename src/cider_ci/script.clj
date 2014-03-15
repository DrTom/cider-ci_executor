; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.script
  (:require 
    [cider-ci.exec :as exec]
    [clojure.tools.logging :as logging]
    [clj-commons-exec :as commons-exec]
    )
  (:use 
    [clj-logging-config.log4j :only (set-logger!)]
    )
  )


;(set-logger! :level :debug)

;; ### EXECUTOR AGENTS ########################################################

; handle prepare-executor scripts with an agent 
(defonce script-exec-agents (atom {}))

(defn script-exec-agent [id]
  "returns (and creates if necessary) the agent for the executor"
  (if-let [script-exec-agent (@script-exec-agents id)]
    script-exec-agent
    (do
      (swap! script-exec-agents 
             (fn [script-exec-agents id]
               (conj script-exec-agents {id (agent {}
                                                   :error-mode :continue)}))
             id)
      (@script-exec-agents id)
      )))

(defn use-memoized-or-execute [agent-state script]
  (let [res (agent-state (:name script))
        prev_state (:state res)]
    (if (= prev_state "success")
      agent-state
      (let [script-exec-result (exec/exec-script-for-params script)]
        (conj agent-state 
              {(:name script) 
               (select-keys script-exec-result
                            [:stderr :stdout :error :exit_status
                             :state :interpreter
                             :started_at :finished_at])})))))

(defn memoized-executor-exec [script]
  (let [my-agent (script-exec-agent (:cider-ci_execution_id script))]
    (send-off my-agent use-memoized-or-execute script)
    (await my-agent)
    (@my-agent (:name script))))

;; ###########################################################################


(defn terminate-services [scripts result-handler]
  (doseq [script-atom scripts]
    (when (= "service" (:type @script-atom))
      (let [new-params (exec/stop-service @script-atom)
            final-params (conj @script-atom new-params)]
        (reset! script-atom final-params)
        (when result-handler (result-handler final-params))))))

(defn process [scripts process-result-handler] 
  (logging/info (str "processing scripts: " scripts))

  (loop [scripts scripts 
         has-failures false]
    (when-let [script-atom (first scripts)] 
      (let [script @script-atom]
        (logging/debug "processing script: "  script)
        (logging/debug "dispatching on the script type "  (:type script))

        (let [script-exec-result 

              (conj script 

                    (case (:type script)

                      "prepare_executor" (memoized-executor-exec script)

                      "service" (if (not has-failures)
                                  (exec/start-service-process script)
                                  {:state "skipped" 
                                   :error "skipped because of previous failure"})

                      ("main" nil) (if (not has-failures)
                                     (exec/exec-script-for-params script)
                                     {:state "skipped" 
                                      :error "skipped because of previous failure"})

                      "clanup_executor" (do
                                          (logging/warn "TODO store and process cleanup-executor")
                                          {:state "success" 
                                           :stdout "Execution is deferred and might not be carried out at all."} )

                      "post_process" (exec/exec-script-for-params script)

                      {:state "failed"
                       :error (str "I don't know what to do with the type " (:type script) "\n" 
                                   "The following types are handled: main prepare_executor post_process cleanup-executor \n"
                                   "Undefined types will be handled like the main type."
                                   )}))]

          (logging/debug "executed script: " script " with result: " script-exec-result)
          (swap! script-atom (fn [script script-exec-result] (conj script script-exec-result)) script-exec-result)
          (when process-result-handler (process-result-handler script-exec-result))
          (recur (rest scripts) 
                 (or has-failures  (not= "success" (:state script-exec-result))))))))
  
   (terminate-services scripts process-result-handler))

