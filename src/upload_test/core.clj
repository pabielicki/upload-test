(ns upload-test.core
  (:gen-class)
  (:use
   upload-test.sftp-upload)
  (:require
    [clojure.java.io :as io]
    [again.core :as again]
    [upload-test.retries :refer :all]
    [clojure.tools.cli :refer [cli]]))


(def required-opts #{:host :port :file :destination :username :rsa-path})

(defn missing-required?
  "Returns true if opts is missing any of the required-opts"
  [opts]
  (not-every? opts required-opts))

(defn file-size [filename]
  (.length (io/file filename)))

(defn check-progress
  [f]
  (let [up-progress (atom 0)]

    (loop [retries 0]
      (when-not (try (f)
               (catch Exception e
                 (prn (get-in (ex-data e) [:progress]))
                 (if (< 0 (- (get-in (ex-data e) [:progress]) @up-progress))
                   (do (reset! up-progress (get-in (ex-data e) [:progress])) nil)
                   (throw e))))
        (do (prn "Retry") (recur (inc retries)))))))

(defn upload-with-retries [opts]
  (time
    (let [progress (atom 0)]
      (check-progress
        #(again/with-retries
          [5000 5000 5000]
          (upload-to-sftp progress
                          (:host opts) (Integer. (:port opts)) (:file opts) (file-size (:file opts))
                          (if (:name opts) (:name opts) (:file opts))
                          (:destination opts) (:username opts) (:rsa-path opts)))))))

(defn -main [& args]
  (let [[opts args banner] (cli args
                                ["-h" "--help" "Print this help"
                                 :default false :flag true]
                                ["-f" "--file"]
                                ["-n" "--name"]
                                ["-d" "--destination"]
                                ["-u" "--username"]
                                ["-ip" "--host"]
                                ["-p" "--port"]
                                ["-rsa" "--rsa-path"])]
    (if (or (:help opts)
              (missing-required? opts))
      (println banner)
      (upload-with-retries opts))))


(comment
  (-main "-ip" "192.168.1.105" "-p" 22 "-f" "test8.tar.gz" "-d" "Pulpit/takietam" "-u" "kisiel" "-rsa" "/home/paveu/.ssh/id_rsa.test")
  ;(-main "-c" 5 "-ip" "213.222.210.140" "-p" 22002 "-f" "test4.tar.gz" "-d" "Upload" "-u" "test" "-rsa" "/home/paveu/Pulpit/test")
  )
