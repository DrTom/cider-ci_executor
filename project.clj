; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(defproject cider-ci_executor "1.0.0"
  :description "Executor for the Cider-CI."
  :url "https://github.com/DrTom/cider-ci_executor"
  :license {:name "GNU Affero General Public License"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [clj-http "0.7.5"]
                 [clj-logging-config "1.9.10"]
                 [clj-time "0.5.1"]
                 [clj-yaml "0.3.1"]
                 [compojure "1.1.5"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [me.raynes/fs "1.4.4"]
                 [org.bouncycastle/bcpkix-jdk15on "1.48"]
                 [org.bouncycastle/bcprov-jdk15on "1.48"]
                 [org.clojars.hozumi/clj-commons-exec "1.0.7"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.2"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.slf4j/slf4j-log4j12 "1.7.5"]
                 [ring "1.1.8"] 
                 [ring/ring-jetty-adapter "1.1.8"]
                 [ring/ring-json "0.2.0"]
                 [robert/hooke "1.3.0"]
                 ]
  :aot [cider-ci.main]
  :main cider-ci.main 
  :plugins [[lein-midje "3.0.0"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]}}
)
