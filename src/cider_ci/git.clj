; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.git
  (:import 
    [java.io File]
    )
  (:require 
    [clojure.pprint :as pprint]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    [cider-ci.shared :as shared]
    [cider-ci.util :as util]
    )
  (:use 
    [clj-logging-config.log4j :only (set-logger!)]
    ))


;(set-logger! :level :debug)
;(set-logger! :level :info)

(defonce repository-agents-atom (atom {}))

(defn canonical-repository-path [prepository-id]
  (str (:git-repos-dir @shared/conf) (File/separator) prepository-id))

(defn create-mirror-clone [url path] 
  (logging/debug (str "create-mirror-clone " url " " path))
  (util/exec-successfully-or-throw ["git" "clone" "--mirror" url path]))

(defn repository-includes-commit? [path commit-id]
  "Returns false if there is an exeception!" 
  (logging/debug (str "repository-includes-commit?"))
  (let [res (util/exec ["git" "log" "-n" "1" "--format='%H'" commit-id]
                       {:dir path})] 
    (= 0 (:exit res))))

(defn update [path]
  (logging/debug (str "update " path))
  (util/exec-successfully-or-throw ["git" "remote" "update"] {:dir path}))

(defn get-or-create-repository-agent [repository-url repository-id]
  (or (@repository-agents-atom repository-id)
      ((swap! repository-agents-atom 
              (fn [repository-agents repository-url repository-id ]
                (conj repository-agents 
                      {repository-id 
                       (agent {:commit-ids #{} 
                               :repository-id repository-id
                               :repository-url repository-url
                               :repository-path (canonical-repository-path repository-id) } 
                              :error-mode :continue)}))
              repository-url repository-id) 
       repository-id)))

(defn initialize-or-update-if-required [agent-state repository-url repository-id commit-id]
  (logging/debug " initialize-or-update-if-required" agent-state repository-id repository-id commit-id)
  (let [repository-path (:repository-path agent-state)]
    (when-not 
      (and (.isDirectory (File. repository-path)) 
           (util/exec-successfully? ["git" "rev-parse"] {:dir repository-path}))
      (create-mirror-clone repository-url repository-path))
    (when-not (repository-includes-commit? repository-path commit-id)
      (update repository-path))
    (conj agent-state {:commit-ids (conj (:commit-ids agent-state) commit-id)})))

(defn serialized-initialize-or-update-if-required [repository-url repository-id commit-id]
  (logging/debug " serialized-initialize-or-update-if-required " repository-url repository-id commit-id)
  (if-not (and repository-url repository-id commit-id)
    (throw (java.lang.IllegalArgumentException. "serialized-initialize-or-update-if-required")))
  (let [repository-agent (get-or-create-repository-agent repository-url repository-id)
        state @repository-agent]
    (logging/debug repository-agent)
    (when-not ((:commit-ids state) commit-id)
      (let [res-atom (atom nil)
            fun (fn [agent-state] 
                  (try 
                    (reset! res-atom
                            (initialize-or-update-if-required agent-state 
                                                              repository-url repository-id commit-id))
                    @res-atom
                    (catch Exception e
                      (reset! res-atom e)
                      agent-state)))]
        (logging/debug "sending-off and await initialize-or-update-if-required")
        (send-off repository-agent fun)
        (await repository-agent)
        (while (nil? @res-atom) (Thread/sleep 100))
        (when (instance? Exception @res-atom)
          (throw @res-atom))))
    (:repository-path @repository-agent)))

(defn clone-to-working-dir [repository-path commit-id working-dir]
  (logging/debug "clone-to-working-dir " repository-path " " commit-id " " working-dir)
  (logging/debug " git " " clone " " --shared " repository-path working-dir)
  (util/exec-successfully-or-throw ["git" "clone" "--shared" repository-path working-dir])
  (let [git-checkout-args ["git" "checkout" commit-id]
        git-checkout-params {:dir working-dir}]
    (logging/debug "GIT CHECKOUT" git-checkout-args " " git-checkout-params)
    (util/exec-successfully-or-throw git-checkout-args git-checkout-params))
  true)

(defn prepare-and-create-working-dir [params]
  (logging/debug "prepare-and-create-working-dir" params)
  (let [working-dir-id (:ci_trial_id params)]
    (when-not (and working-dir-id (not (clojure.string/blank? working-dir-id)))
      (throw (java.lang.IllegalArgumentException. "invalid arguments of prepare-and-create-working-dir" )))
    (let [repository-path (serialized-initialize-or-update-if-required 
                            (:git_url params) (:repository_id params) (:git_commit_id params))
          working-dir (str (:working-dir @shared/conf) (File/separator) working-dir-id) ]
      (clone-to-working-dir repository-path (:git_commit_id params) working-dir)
      (let [submodules (:git_submodules params)]
        (logging/debug "SUBMODULES " submodules)
        (doseq [submodule submodules]
          (logging/debug "SUBMODULE " submodule)
          (let [submodule-repository-path (serialized-initialize-or-update-if-required 
                                            (:git_url submodule) (:repository_id submodule) (:git_commit_id submodule))
                submodule-working-dir (clojure.string/join File/separator (concat [working-dir] (:subpath_segments submodule)))]
            (util/exec-successfully-or-throw ["mkdir" "-p" submodule-working-dir])
            (clone-to-working-dir submodule-repository-path (:git_commit_id submodule) submodule-working-dir))))
      (logging/debug "WORKING-DIR " working-dir)
      working-dir)))


; FOR PROTOTYPING
; (create-repository-agent "http://localhost:3013/repositories/PrototypeRepo/git" "TestX")
; (def repo-path (serialized-initialize-or-update-if-required "http://localhost:3013/repositories/PrototypeRepo/git" "TestX" "e4e1e98"))

