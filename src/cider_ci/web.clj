; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.web
  (:use 
    [clj-logging-config.log4j :only (set-logger!)]
    )
  (:require 
    [clj-time.core :as time]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [clojure.stacktrace :as stacktrace]
    [clojure.tools.logging :as logging]
    [compojure.core]
    [compojure.handler]
    [cider-ci.certificate :as certificate]
    [cider-ci.trial :as trial]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json]
    ))

;(set-logger! :level :debug)

(defonce conf (atom {:host "0.0.0.0"
                     :port 8088
                     :ssl-port 8443}))

(defn say-hello []
  (str "<h1>Hello!</h1>"))

(defn ping [] 
  (logging/debug "pinging back")
  {:status 204})

(defn execute [request]
  (logging/info (str "received execution request: " request))
  (try 
    (let [trial-parameters  (clojure.walk/keywordize-keys (:json-params request))]
      (logging/debug "trial-parameters" trial-parameters)
      (when-not (:trial_id trial-parameters) (throw (IllegalStateException. ":trial_id parameter must be present")))
      (when-not (:patch_url trial-parameters) (throw (IllegalStateException. ":patch_url parameter must be present")))
      (future (trial/execute trial-parameters))
      {:status 204})
    (catch Exception e
      (logging/error request (with-out-str (stacktrace/print-stack-trace e)))
      {:status 422 :body (str e)})))


(defn get-trials []
  (let [trials (trial/get-trials)]
     {:status 201 
      :headers {"Content-Type" "application/json"}
      :body (json/write-str trials)})) 

(compojure.core/defroutes app-routes
  (compojure.core/GET "/hello" [] (say-hello))
  (compojure.core/POST "/ping" [] (ping))
  (compojure.core/POST "/execute" req (execute req))
  (compojure.core/GET "/trials" [] (get-trials)))

(def app
  ( -> (compojure.handler/site app-routes)
       (ring.middleware.json/wrap-json-params)))


(defonce server nil)

(defn stop-server []
  (logging/info "stopping server")
  (. server stop)
  (def server nil))


(defn start-server []
  "Starts (or stops and then starts) the webserver"
  (let [keystore (certificate/create-keystore-with-certificate)
        path (.getAbsolutePath (:file keystore))
        password (:password keystore) 
        server-conf (conj {:ssl? true 
                           :keystore path
                           :key-password password
                           :join? false} @conf)]
    (if server (stop-server)) 
    (logging/info "starting server" server-conf)
    (def server (jetty/run-jetty app server-conf))))
