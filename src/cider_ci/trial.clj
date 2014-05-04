; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 
 
(ns cider-ci.trial
  (:import 
    [java.io File ]
    )
  (:require
    [clj-commons-exec :as commons-exec]
    [clj-time.core :as time]
    [clojure.pprint :as pprint]
    [clojure.stacktrace :as stacktrace]
    [clojure.tools.logging :as logging]
    [cider-ci.attachments :as attachments]
    [cider-ci.exec :as exec]
    [cider-ci.git :as git]
    [cider-ci.reporter :as reporter]
    [cider-ci.port-provider :as port-provider]
    [cider-ci.script :as script]
    [cider-ci.util :as util]
    [cider-ci.with :as with]
    )
  (:use 
    [clj-logging-config.log4j :only (set-logger!)]
    ))

;(set-logger! :level :debug)

(defn create-update-sender-via-agent [report-agent]
  (fn [params]
    (let [url (:patch_url params)
          fun (fn[agent-state]
                (let [res (reporter/put-as-json-with-retries url params)]
                  (conj agent-state params)))]
      (send-off report-agent fun))))


(defn send-trial-patch 
  "Sends just the patch-params" 
  [report-agent params patch-params]
  ((create-update-sender-via-agent report-agent) 
   (conj (select-keys params [:patch_url])
         patch-params)))


(defn- set-and-send-start-params [params-atom report-agent]
  (swap! params-atom (fn [params] (conj params {:state "executing"}))) 
  (send-trial-patch report-agent @params-atom (select-keys @params-atom [:state :started_at])))

(defn- prepare-and-insert-scripts [params-atom]
  (let [initial-scripts (:scripts @params-atom)
        script-atoms (map (fn [script-params]
                            (atom (conj script-params 
                                        (select-keys @params-atom
                                                     [:environment_variables 
                                                      :execution_id 
                                                      :trial_id 
                                                      :working_dir ]))))
                          initial-scripts)]
    (swap! params-atom #(conj %1 {:scripts %2}) script-atoms)
    script-atoms))



(defonce ^:private trials-atom (atom {}))
(defn get-trials [] @trials-atom)

;(clojure.pprint/pprint trials-atom)
 
(defn- create-trial   
  "Creates a new trial, stores it in trials under it's id and returns the
  trial"
  [params]
  (let [id (:trial_id params)]
    (swap! trials-atom 
           (fn [trials params id]
             (conj trials {id {:params-atom (atom  params)
                               :report-agent (agent [] :error-mode :continue)}}))
           params id)
    (@trials-atom id)))

(defn- delete-trial [id trial]
  (logging/debug "deleting trial " id)
  (when (= 0 (:exit @(commons-exec/sh ["rm" "-rf" (:working_dir trial)])))
    (swap! trials-atom #(dissoc %1 %2) id)))

; ### BEGIN clean trials #######################################

(defonce ^:private trials-cleaner-stopped (atom false))

(defn start-trials-cleaner []
  (logging/info "started trials cleaner")
  (swap! trials-cleaner-stopped (fn [_] false)) 
  (future
    (loop [] 
      (doseq [[id trial] @trials-atom] 
        (logging/debug "deleting? " id trial)
        (with/logging-and-swallow
          (let [params @(:params-atom trial) 
                timestamp (or (:finished_at params) (:started_at params))]
            (when (> (time/in-minutes (time/interval timestamp (time/now))) 150)
              (delete-trial id trial)))))
      (Thread/sleep (* 60 1000))
      (if-not @trials-cleaner-stopped 
        (recur)
        (logging/info "stopped trials cleaner")))))

(defn clean-all []
  (doseq [[id trial] @trials-atom] 
    (logging/debug "deleting? " id trial)
    (with/logging-and-swallow
      (delete-trial id trial))))
  
(defn stop-trials-cleaner []
  (swap! trials-cleaner-stopped (fn [_] true)))

; ### END clean trials #######################################

(defn initialize []
  (.addShutdownHook 
    (Runtime/getRuntime)
    (Thread. (fn [] (clean-all))))
  (start-trials-cleaner))


(defn execute [params] 
  (logging/info execute [params])
  (let [started-at (time/now)
        trial (create-trial (conj params {:started_at started-at}))
        report-agent (:report-agent trial)
        params-atom (:params-atom trial)]
    (try 
      (let [
            working-dir (git/prepare-and-create-working-dir params)
            _ (swap! params-atom #(conj %1 {:working_dir %2}) working-dir) 
            scripts-atoms (prepare-and-insert-scripts  params-atom)
            ports (into {} (map (fn [[port-name port-params]] 
                                  [port-name (port-provider/occupy-port 
                                               (or (:inet_address port-params) "localhost") 
                                               (:min port-params) 
                                               (:max port-params))])
                                (:ports @params-atom)))]
        (try 

          (set-and-send-start-params params-atom report-agent)

          ; inject the ports into the scripts
          (doseq [script-atom scripts-atoms]
            (swap! script-atom #(conj %1 {:ports %2}) ports))

          (script/process scripts-atoms  nil)

          (let [final-state (if (every? (fn [script-atom] 
                                          (= "success" (:state @script-atom))) 
                                        scripts-atoms) "success" "failed")]

            (swap! params-atom 
                   #(conj %1 {:state %2, :finished_at (time/now)}) 
                   final-state)

            ((create-update-sender-via-agent report-agent) @params-atom))

          (future (attachments/put working-dir 
                                   (:attachments @params-atom) 
                                   (:attachments_url @params-atom)))

          (finally 
            (doseq [[_ port] ports]
              (port-provider/release-port port))
            trial)))

      (catch Exception e
        (swap! params-atom (fn [params] 
                             (conj params 
                                   {:state "failed", 
                                    :finished_at (time/now)
                                    :error  (with-out-str (stacktrace/print-stack-trace e))})))
        (logging/error  (str @params-atom (with-out-str (stacktrace/print-stack-trace e))))
        ((create-update-sender-via-agent report-agent) @params-atom))
      (finally trial))
    trial))

