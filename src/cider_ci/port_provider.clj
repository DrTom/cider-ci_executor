; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 
 
(ns cider-ci.port-provider
  (:import 
    [java.net ServerSocket DatagramSocket InetAddress]
    )
  (:require
    [clj-logging-config.log4j :as  logging-config]
    [clojure.pprint :as pprint]
    [clojure.stacktrace :as stacktrace]
    [clojure.tools.logging :as logging]
    [cider-ci.util :as util]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

(defonce ^:private occupied-ports-atom (atom #{}))

(defn release-port [id]
  (swap! occupied-ports-atom #(disj %1 %2) id))

; (release-port 3000)
; (occupy-port "localhost" 3000 3001)

; see 
; http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
; however, it seem rather unreliable to detect ports that are already used.
;
(defn occupy-port 
  "Tries to find an unoccupied and unused port in the range [range-min
  range-max]. Inet-address can be a hostname, ipv4, or ipv6 address. Including
  the 'listen to all', e.g. '0.0.0.0'.  Returs the port as an integer if
  successful. Throws an exception if not. Occupied ports must be released with
  (release-port port)."

  ([inet-address range-min range-max]
   (occupy-port inet-address range-min range-max 0))

  ([inet-address range-min range-max retry]

   (assert (< range-min range-max))

   (if (> retry 10)
     (throw (IllegalStateException. 
              (str "No unoccupied and unused port was found in the range " range-min " to " range-max))))

   (let [port (+ range-min (rand-int (+ 1 (- range-max range-min))))]
     (if (contains?  @occupied-ports-atom port)
       (occupy-port inet-address range-min range-max (inc retry))
       (try 
         (logging/debug "testing port " port)
         (with-open [ss (ServerSocket. port 10 (InetAddress/getByName inet-address))
                     ds (DatagramSocket. port (InetAddress/getByName inet-address))]
           (swap! occupied-ports-atom #(conj %1 %2) port)
           port)
         (catch Exception e
           (occupy-port inet-address range-min range-max (inc retry))))))))
