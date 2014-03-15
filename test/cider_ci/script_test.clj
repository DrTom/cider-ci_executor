; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 


(ns cider-ci.script-test
  (:require  
    [clojure.pprint :as pprint]
    [cider-ci.util :as util]
    )
  (:use 
    [clojure.test]
    [cider-ci.script]
    [midje.sweet]
  ))


(deftest test-memoized-executor-exec

  (testing "a successful script" 

    (let [script-params {:name "list-env"
                         :body "env | sort"
                         :working_dir  (System/getProperty "user.home")
                         :environment_variables {:cider-ci_task_id (util/random-uuid)
                                                 :cider-ci_trial_id (util/random-uuid)
                                                 :cider-ci_execution_id (util/random-uuid)}}]

      (testing "invoking memoized-executor-exec" 
        (let [res (memoized-executor-exec  script-params)]

          (testing "the result contains the script execution result"
            (is (contains? res :error))
            (is (contains? res :exit_status))
            (is (contains? res :finished_at))
            (is (contains? res :interpreter))
            (is (contains? res :started_at))
            (is (contains? res :state))
            (is (contains? res :stderr))
            (is (contains? res :stdout)))

          (testing "the result state is success" 
            (is (= (:state res) "success")))

          ;(testing "stdout of 'env | sort' includes the defined variables"
          ;  (is (not= nil (re-find #"(?is)UUID" (:stdout res)))))

          (testing "the agent for the execution_sha1"
            (let [exec-agent  (@script-exec-agents (:cider-ci-execution-uuid script-params))]
              (testing "is present and is an agent"
                (= (type exec-agent) clojure.lang.Agent))
              (testing "it stores the script result under its name" 
                (let [memoized-res (@exec-agent  (:name script-params))]
                  (not= nil memoized-res)
                  ))

              ))))))


  (testing "a successful script" 
    (let [script-params {:name "failing one"
                         :body "env |sort; exit -1"
                         :working_dir  (System/getProperty "user.home")
                         :environment_variables {:cider-ci_task_id (util/random-uuid)
                                                 :cider-ci_trial_id (util/random-uuid)
                                                 :cider-ci_execution_id (util/random-uuid)}}]
      (testing "invoking memoized-executor-exec" 
        (let [res (memoized-executor-exec  script-params)]
          (println (str res))
          (testing "the result contains the script execution result"
            (is (contains? res :error))
            (is (contains? res :exit_status))
            (is (contains? res :finished_at))
            (is (contains? res :interpreter))
            (is (contains? res :started_at))
            (is (contains? res :state))
            (is (contains? res :stderr))
            (is (contains? res :stdout))))
        ))))
