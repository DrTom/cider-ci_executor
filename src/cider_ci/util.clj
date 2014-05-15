; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.util
  (:import
    [java.util UUID]
    )
  (:require 
    [clj-commons-exec :as commons-exec]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clj-yaml.core :as yaml]
    [clojure.pprint :as pprint]
    [clojure.stacktrace :as stacktrace]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    )
  (:use 
    [clj-logging-config.log4j :only (set-logger!)]
    ))


;(set-logger! :level :debug)

(declare 
  ammend-config
  application-trace
  date-time-to-iso8601
  exec-successfully-or-throw
  filter-trace
  hash-to-env-opts
  random-uuid
  rubyize-keys
  split-in-dirs
  try-read-and-apply-config
  upper-case-keys
  uuid-to-short)


(defn ammend-config [conf_atom data]
  (logging/info "amending config " conf_atom " with " data)
  (swap! conf_atom 
         (fn [current more] 
           (conj current more))
         data))

(defn application-trace [tr]
  (filter-trace tr #".*cider-ci.*"))

(defn date-time-to-iso8601 [date-time]
  (time-format/unparse (time-format/formatters :date-time) date-time))

(defn now-as-iso8601 [] (date-time-to-iso8601 (time/now)))
(defn exec [& args]
  (logging/debug "exec" args)
  (let [res @(apply commons-exec/sh args)]
    res ))

(defn exec-successfully? 
  "Returns false if the shell execution specified by
  args did not exited with 0. Returns the result of the
  execution otherwise."
  [& args]
  (logging/debug "exec-successfully? " args)
  (let [res @(apply commons-exec/sh args)]
    (if (= 0 (:exit res))
      res
      false)))

(defn exec-successfully-or-throw [& args]
  (logging/debug "exec-successfully-or-throw" args)
  (let [res @(apply commons-exec/sh args)]
    (if (not= 0 (:exit res))
      (throw (IllegalStateException. 
               (str "Unsuccessful shell execution " 
                    args (:err res) (:out res)))) 
      res)))

(defn filter-trace [tr regex]
  (concat [(with-out-str (stacktrace/print-throwable tr))]
          (filter (fn [l] (re-matches regex l))
                  (map (fn [e] (with-out-str (stacktrace/print-trace-element e)))
                       (.getStackTrace tr)
                       ))))

(defn hash-to-env-opts [h]
  (map #(str (string/upper-case (name (first %1))) "=" (last %1)) h))

(defn random-uuid []
  (.toString (java.util.UUID/randomUUID)))

(defn rubyize-keys [some-hash]
  (into {} (map (fn [p] [(keyword (string/replace (name (first p)) #"-" "_")) (second p)] ) some-hash)))

(defn split-in-dirs [s]
  (let [dirs-seq1  (string/split s #"/")
        dirs-seq2 (filter #(not (string/blank? %)) dirs-seq1)
        ]
    dirs-seq2
    ))

(defn try-read-and-apply-config [configs & filenames]
  (doseq [file-ending ["clj" "yml"]]
    (doseq [basename filenames]
      (let [filename (str basename "." file-ending)]
        (try 
          (when-let [config-string (slurp filename)]
            (logging/info "successfully read " filename)
            (when-let [file-config (cond (re-matches #"(?i).*yml" filename) (yaml/parse-string config-string)
                                         (re-matches #"(?i).*clj" filename) (read-string config-string)
                                         :else (throw (IllegalStateException. (str "could not determine parser for " filename))))]
              (logging/info "successfully read " filename " with content: " file-config)
              (doseq [[k config] configs]
                (if-let [config-section (k file-config)]
                  (do 
                    (logging/info "amending config " k)
                    (ammend-config
                      config
                      config-section))))))
          (catch Exception e (do (logging/info (str "could not read " filename " " e))))
          )))))

(defn upper-case-keys [some-hash]
  (into {} (map (fn [p] [ (string/upper-case (name (first p))) (second p)] ) some-hash)))

(defn uuid-to-short [uuid]
  (let [juuid (UUID/fromString uuid)
        least-sig-bytes (.getLeastSignificantBits juuid) ]
    (-> least-sig-bytes .intValue .shortValue (- Short/MIN_VALUE))
    ))


(defn logit [f & args]
  (logging/info f " ARGS: " args)
  (let [res (apply f args)]
    (logging/info f " RESULT: " res)
    res ))


