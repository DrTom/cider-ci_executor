; Copyright (C) 2013 Dr. Thomas Schank 
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.git-test
  (:import 
    [java.io File]
    )
  (:require  
    [clojure.java.shell :as shell]
    [clojure.pprint :as pprint]
    [cider-ci.shared :as shared]
    )
  (:use 
    [clojure.test]
    [cider-ci.git]
  ))

(defn create-tmp-dir [prefix]
  (let [path (java.nio.file.Files/createTempDirectory prefix (make-array java.nio.file.attribute.FileAttribute 0))]
    (.deleteOnExit (.toFile path))
    (.toString path)))

(defn config-setup! []
  (let [tmp-dir (System/getProperty "java.io.tmpdir")]
    (swap! shared/conf (fn [orig-conf]
                       (conj orig-conf 
                             {:working-dir (create-tmp-dir "cider-ci-executer-test-working-dir_")
                              :git-repos-dir (create-tmp-dir "cider-ci-executer-git-repo-dir_")
                              :tmp-dir (create-tmp-dir "cider-ci-executer-tmp-dir_") 
                              :project-dir (.getCanonicalPath (java.io.File. "."))}
                             )))))

(defn unpack-test-repo! []
  (let [repo-tar-file (str (:project-dir @shared/conf) (File/separator) "test"  (File/separator) "data" (File/separator) "test_repo.tar.gz")
        tmp-dir (:tmp-dir @shared/conf)
        res (shell/sh "tar" "xfv" repo-tar-file :dir tmp-dir)]
    (if (not= 0 (:exit res)) (throw (Exception. (:err res))))
    (swap! shared/conf (fn [orig-conf tmp-dir]
                       (conj orig-conf 
                             {:test-repo-path (str tmp-dir (File/separator) "test_repo")}))
           tmp-dir)))


(deftest git
  (testing "prepare-and-create-working-dir"
    (config-setup!)
    (unpack-test-repo!)
    (let [working-dir (prepare-and-create-working-dir {:git_url (:test-repo-path @shared/conf) 
                                                      :repository_id  "test_repo"
                                                      :git_commit_id "e4e1e98473b51b5539a16741da717f4e2ae33965"
                                                      :trial_id (str (java.util.UUID/randomUUID)) 
                                                      })]
      (testing "it clones the test_repo"
        (let [res (shell/sh "git" "log" "-n" "1" "--format='%H'" "e4e1e98473b51b5539a16741da717f4e2ae33965" 
                            :dir (:test-repo-path @shared/conf))]
          (is (= 0 (:exit res)))))


      (testing "the working dir is checked out git repo with a commit present"
        (let [res (shell/sh "git" "log" "-n" "1" "--format='%H'" "e4e1e98473b51b5539a16741da717f4e2ae33965" 
                            :dir working-dir)]
          (is (= 0 (:exit res))))
        (is (= true (.exists (File.  (str working-dir (File/separator) "README.mdk"))))) )
      )))


