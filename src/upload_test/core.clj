(ns upload-test.core
  (:gen-class)
  (:use
   upload-test.sftp-upload)
  (:require
    [clojure.java.io :as io]
    [again.core :as again]
    [upload-test.retries :refer :all]
    [clojure.tools.cli :refer [cli]])
  (:import org.apache.commons.io.input.BoundedInputStream))


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
                 (if (< 0 (- (get-in (ex-data e) [:progress]) @up-progress))
                   (do (reset! up-progress (get-in (ex-data e) [:progress])) nil)
                   (throw e))))
        (do (prn "Retry") (recur (inc retries)))))))
gi
(defn upload-async
  [host port input name destination username rsa-path]
  (future
    (let [progress (atom 0)]
      (check-progress
        #(again/with-retries [100 100 100]
                             (upload-to-sftp progress host port input name destination username rsa-path))))))

(defn rounded-single-chunk-size
  [file count]
  (long (Math/ceil (/ (file-size file) count))))

(defn chunks-list
  [name start size]
  {:name (apply str name ".part" (str start)) :start (* start size) :size size})

(defn just-chunk
  [file count]
  (let [size (rounded-single-chunk-size file count)]
    (map #(chunks-list file % size) (range count))))

(defn chunk-and-send-file
  [file count host port destination username rsa-path]
  (->>
    (just-chunk file count)
    (map
      #(let [input (io/input-stream file)]
        (.skip input (get-in % [:start]))
        (let [bounded (BoundedInputStream. input (get-in % [:size]))]
          (upload-async host port bounded (get-in % [:name]) destination username rsa-path))))
    (map deref)
    (doall)))

(defn chunked_or_standard_upload [opts]
  (if (:chunks opts)
    (time
      (chunk-and-send-file (:file opts) (Integer. (:chunks opts)) (:host opts) (Integer. (:port opts))
                           (:destination opts) (:username opts) (:rsa-path opts)))
    (time
      (let [progress (atom 0)]
        (check-progress
         #(again/with-retries
           [100 100 100]
           (upload-to-sftp progress
             (:host opts) (Integer. (:port opts)) (:file opts)
             (if (:name opts) (:name opts) (:file opts))
             (:destination opts) (:username opts) (:rsa-path opts))))))))

(defn -main [& args]
  (let [[opts args banner] (cli args
                                ["-h" "--help" "Print this help"
                                 :default false :flag true]
                                ["-c" "--chunks"]
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
      (chunked_or_standard_upload opts))))


(comment
  (upload-async "192.168.1.105" 22 "x.txt" "lallaa" "Pulpit/takietam/tests" "kisiel" "/home/paveu/.ssh/id_rsa.test")
  (time (chunk-and-send-file "x.txt" 5 "192.168.1.105" 22 "Pulpit/takietam/tests" "kisiel" "/home/paveu/.ssh/id_rsa.test"))
  (time (x "x.txt" 5 "192.168.1.105" 22 "Pulpit/takietam/tests" "kisiel" "/home/paveu/.ssh/id_rsa.test"))
  (-main "-c" 2 "-ip" "192.168.1.105" "-p" 22 "-f" "backup.tar.bz2" "-d" "Pulpit/takietam/tests" "-u" "kisiel" "-rsa" "/home/paveu/.ssh/id_rsa.test")
  )