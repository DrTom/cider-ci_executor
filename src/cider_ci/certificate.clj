; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.certificate
  (:import
    [java.security KeyPairGenerator KeyStore Security SecureRandom]
    [org.bouncycastle.jce X509Principal]
    [org.bouncycastle.jce.provider BouncyCastleProvider]
    [org.bouncycastle.x509 X509V3CertificateGenerator]
    [java.util Date]
    [java.io File FileOutputStream]
    ))

(defn- create-certificate []

  (Security/addProvider (BouncyCastleProvider.))

  (let [generator (KeyPairGenerator/getInstance  "RSA")
        _  (.initialize generator 2048 (SecureRandom.))
        key-pair (.generateKeyPair generator)
        cert_gen (X509V3CertificateGenerator.) 
        year_in_millis (* 1000 60 60 24 365)
        ]

    (.setSerialNumber cert_gen  (biginteger (rand-int 1000000000)))
    (.setIssuerDN cert_gen (X509Principal. "CN=cn, O=o, L=L, ST=il, C=c"))
    (.setSubjectDN cert_gen (X509Principal. "CN=cn, O=o, L=L, ST=il, C=c"))
    (.setNotBefore cert_gen (Date. (- (System/currentTimeMillis) year_in_millis )))
    (.setNotAfter cert_gen (Date. (+ (System/currentTimeMillis) (* year_in_millis 100))))
    (.setPublicKey cert_gen (.getPublic key-pair))
    (.setSignatureAlgorithm cert_gen "SHA256WithRSAEncryption")

    {:x509cert (.generateX509Certificate cert_gen (.getPrivate key-pair))
    :key-pair key-pair}

    ))

(defn- create-tmp-keystore-file []
  (let [ keystore-file (File/createTempFile "cider-ci_ci_jvm_executor_", ".keystore") ]
    (.deleteOnExit keystore-file)
    keystore-file))

(defn- create-keystore-and-save-certificate [certificate keystore-file]
  (let [keystore (KeyStore/getInstance "JKS") 
        cert-alias "Unsigned cider-ciCI Executor Certificate"  
        private-key (.getPrivate (:key-pair certificate))
        password (.toString (java.util.UUID/randomUUID))
        password_char_array (.toCharArray password)
        ]
    (.load keystore nil nil)
    (.setKeyEntry keystore cert-alias private-key password_char_array (into-array [(:x509cert certificate)]))
    (.store keystore (FileOutputStream. keystore-file) password_char_array )
    (conj certificate {:file keystore-file, :password password})
  ))

(defn create-keystore-with-certificate []
  "creates a self signed certificate in a keystore-file, 
  convenient for running a ring adapter with ssl, 
  returns a map with :password and :file among others"
  (let [certificate (create-certificate)
        keystore-file (create-tmp-keystore-file) ]
    (create-keystore-and-save-certificate certificate keystore-file)))
