; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 


(ns cider-ci.script-process-test
  (:require  
    [cider-ci.util :as util]
    )
  (:use
    [clojure.test]
    [cider-ci.script]
    [midje.sweet]
    ))

(facts "process with value 0 exiting scripts" 
       (let [ script1-atom (atom {:name "show env" 
                                  :environment_variables {:cider-ci_task_id (util/random-uuid)
                                                          :cider-ci_trial_id (util/random-uuid)
                                                          :cider-ci_execution_id (util/random-uuid)
                                                          :x 5 
                                                          "Y" 7}
                                  :body "env | sort"
                                  :working_dir  (System/getProperty "user.home")
                                  })
             script2-atom (atom {:name "ls" 
                                 :environment_variables {:cider-ci_task_id (util/random-uuid)
                                                         :cider-ci_trial_id (util/random-uuid)
                                                         :cider-ci_execution_id (util/random-uuid)}
                                 :body "ls -lah"
                                 :working_dir  (System/getProperty "user.home")
                                 })
             scripts [script1-atom,script2-atom]]
         (process scripts nil)

         (fact "sets the state to success" 
               (fact (:state @script1-atom) => "success")
               (fact (:state @script2-atom) => "success"))))



(facts "memoizing: call process twice with the same execution id, random output, and script type of prepare_executor" 
       (let [ execution-id  (util/random-uuid)

             script1-atom (atom {:name "random" 
                                 :type "prepare_executor"
                                 :environment_variables {:cider-ci_task_id (util/random-uuid)
                                                         :cider-ci_trial_id (util/random-uuid)
                                                         :cider-ci_execution_id (util/random-uuid)}
                                 :body "echo $RANDOM"
                                 :working_dir  (System/getProperty "user.home")
                                 })

             script2-atom (atom {:name "random" 
                                 :type "prepare_executor"
                                 :environment_variables {:cider-ci_task_id (util/random-uuid)
                                                         :cider-ci_trial_id (util/random-uuid)
                                                         :cider-ci_execution_id (util/random-uuid)}
                                 :body "echo $RANDOM"
                                 :working_dir  (System/getProperty "user.home")
                                 })

             scripts [script1-atom,script2-atom]]
         (process scripts nil)
         (fact "sets the states to success" 
               (fact (:state @script1-atom) => "success")
               (fact (:state @script2-atom) => "success"))
         (fact "returns the same (momoized) non blank stdout for both scripts" 
               (fact (clojure.string/blank? (:stdout @script1-atom)) => false)
               (fact (:stdout @script1-atom) => (:stdout @script2-atom))
               )scripts))


(facts "service" 
       (let [ execution-id  (util/random-uuid)

             script1-atom (atom {:name "sleeping service" 
                                 :type "service"
                                 :environment_variables {:cider-ci_task_id (util/random-uuid)
                                                         :cider-ci_trial_id (util/random-uuid)
                                                         :cider-ci_execution_id (util/random-uuid)}
                                 :body "sleep 10"
                                 :working_dir  (System/getProperty "user.home")
                                 })

             script2-atom (atom {:name "list env" 
                                 :environment_variables {:cider-ci_task_id (util/random-uuid)
                                                         :cider-ci_trial_id (util/random-uuid)
                                                         :cider-ci_execution_id (util/random-uuid)}
                                 :body "env | sort"
                                 :working_dir  (System/getProperty "user.home")
                                 })

             scripts [script1-atom,script2-atom]]
         (process scripts nil)
         (fact "sets the states to success" 
               (fact (:state @script1-atom) => "success")
               (fact (:state @script2-atom) => "success"))
         (fact "terminates the process" 
               (fact (realized? (:exec_promise @script1-atom)) => true)
               )
         scripts))
