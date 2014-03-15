; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.exec-test
  (:import 
    [org.apache.commons.exec ExecuteWatchdog]
    )
  (:require  
    [clojure.pprint :as pprint]
    [cider-ci.util :as util]
    )
  (:use 
    [clojure.test]
    [cider-ci.exec]
    [midje.sweet]
    ))

(facts "about the return parameters of successful test-exec-script-for-params" 
       (let [def-params {:name "testscript"
                         :body "env | sort"
                         :working_dir  (System/getProperty "user.home")
                         :environment_variables {:cider-ci_task_id (util/random-uuid)
                                                 :cider-ci_trial_id (util/random-uuid)
                                                 :cider-ci_execution_id (util/random-uuid)}}
             res (exec-script-for-params def-params)]
         (fact (contains? res :error) => true)
         (fact (contains? res :exit_status) => true)
         (fact (contains? res :started_at) => true)
         (fact (contains? res :finished_at) => true)
         (fact (contains? res :interpreter) => true)
         (fact (contains? res :started_at) => true)
         (fact (contains? res :state) =>  true)
         (fact (contains? res :stderr) => true)
         (fact (contains? res :stdout) => true)))
       

(facts "about exec-script-for-params running into timeout"
       (let [def-params {:name "testscript"
                         :body "sleep 2"
                         :timeout 1
                         :working_dir  (System/getProperty "user.home")
                         :environment_variables {:cider-ci_task_id (util/random-uuid)
                                                 :cider-ci_trial_id (util/random-uuid)
                                                 :cider-ci_execution_id (util/random-uuid)}}
             res (exec-script-for-params def-params)]
         (fact "the exit_status is defined" :wip
               (not= nil (:exit_status res)) => true)
         (fact "the exit_status is not null" 
               (= 0 (:exit_status res)) => false
               )))

(facts about "starting a long running service with start-service-process" 
       (let [params {:name "service"
                     :body "sleep 60"
                     :working_dir  (System/getProperty "user.home")
                     :environment_variables {:cider-ci_task_id (util/random-uuid)
                                             :cider-ci_trial_id (util/random-uuid)
                                             :cider-ci_execution_id (util/random-uuid)}}
             service (start-service-process params)]
         (fact "it contains a watchdog " 
               (instance? ExecuteWatchdog (:watchdog service)) => true)
         (fact "it contains a not yet realized :exec_promise" 
               (realized? (:exec_promise service)) => false)

         (facts "the service contains all the other properties" 
                (fact (contains? service :error) => true)
                (fact (contains? service :exit_status) => true)
                (fact (contains? service :started_at) => true)
                (fact (contains? service :finished_at) => true)
                (fact (contains? service :interpreter) => true)
                (fact (contains? service :started_at) => true)
                (fact (contains? service :state) =>  true)
                (fact (contains? service :stderr) => true)
                (fact (contains? service :stdout) => true))

         (facts "calling destroyProcess() on the watchdog" 
                (.destroyProcess (:watchdog service))
                (Thread/sleep 500)
                (fact "realizes the promise"
                      (realized? (:exec_promise service)) => true))
         service))

