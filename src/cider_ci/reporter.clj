(ns cider-ci.reporter
  (:use 
    [clojure.stacktrace :only (print-stack-trace)]
    [clj-logging-config.log4j :only (set-logger!)]
    )
  (:require 
    [clj-http.client :as http-client]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clojure.data.json :as json]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    [cider-ci.json]
    ))

;(set-logger! :level :debug)

(defonce conf (atom {:max-retries 10
                     :retry-ms-factor 3000
                     }))

(defn rubyize-keyword [kw]
  (string/replace (name kw) "-" "_"))

(defn put-as-json [url params]
  (logging/info "PUTTING to: " url)
  (let [body (json/write-str params :key-fn rubyize-keyword)]
    (logging/debug "body: " body)
    (http-client/put
      url
      {:insecure? true
       :content-type :json
       :accept :json 
       :body body})))

(defn put-as-json-with-retries 
  ([url params]
   (put-as-json-with-retries url params (:max-retries @conf)))
  ([url params max-retries]
   (loop [retry 0]
     (Thread/sleep (* retry retry (:retry-ms-factor @conf)))
     (let [res  (try
                  (put-as-json url params)
                  {:url url :params params :send-status "success"}
                  (catch Exception e
                    (logging/warn "failed " (inc retry) " time to PUT to " url " with error: " e)
                    {:url url :params params, :send-status "failed" :error e}))]
       (if (= (:send-status res) "success") 
         res
         (if (>= retry max-retries)
           res
           (recur (inc retry))))))))
