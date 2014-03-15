; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.json
  (:require 
    [clojure.data.json]
    [clj-time.core :as time]
    [clj-time.format :as time-format]))

(clojure.core/extend-type clojure.lang.Agent clojure.data.json/JSONWriter
  (-write [object out]
    (clojure.data.json/-write @object out)))

(clojure.core/extend-type clojure.lang.Atom clojure.data.json/JSONWriter
  (-write [object out]
    (clojure.data.json/-write @object out)))

(clojure.core/extend-type org.joda.time.DateTime clojure.data.json/JSONWriter
  (-write [date-time out]
    (clojure.data.json/-write (time-format/unparse (time-format/formatters :date-time) date-time) 
                              out)))

(clojure.core/extend-type java.util.concurrent.FutureTask clojure.data.json/JSONWriter
  (-write [future-task out]
    (clojure.data.json/-write {:done (. future-task isDone)}
                              out)))

(clojure.core/extend-type sun.nio.fs.UnixPath clojure.data.json/JSONWriter
  (-write [path out]
    (clojure.data.json/-write (.toString path)  out)))


(clojure.core/extend-type org.apache.commons.exec.ExecuteWatchdog clojure.data.json/JSONWriter
  (-write [watchdog out]
    (clojure.data.json/-write (.isWatching watchdog)  out)))

(clojure.core/extend-type java.lang.Object clojure.data.json/JSONWriter
  (-write [obj out]
    (clojure.data.json/-write (str "Unhandled type for write json: " (type obj)) out)))
