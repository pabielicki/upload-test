(ns upload-test.sftp-upload
  (:use
    clj-ssh.ssh
    )
  (:require
    [clojure.java.io :as io]
    [upload-test.retries :refer :all]))

(defmacro my-with-connection
  "Creates a context in which the session is connected. Ensures the session is
  disconnected on exit."
  [session timeout & body]
  `(let [session# ~session timeout# ~timeout]
     (try
       (when-not (connected? session#)
         (connect session# timeout#))
       ~@body
       (finally
         (disconnect session#)))))

(defn percentage [progress total]
  (apply str (format "%.2f" (float(* 100 (/ progress total)))) "%"))

(defn sftp-monitor
  "Create a SFTP progress monitor"
  [name file-size]
  (let [operation (atom nil)
        source (atom nil)
        destination (atom nil)
        size (atom nil)
        done (atom false)
        progress (atom 0)
        continue (atom true)]
    [ (proxy [com.jcraft.jsch.SftpProgressMonitor] []
        (init [op src dest max]
          (do
            (println " ")
            (println (apply str "Started " name))
            (println " ")
            (reset! operation op)
            (reset! source src)
            (reset! destination dest)
            (reset! size file-size)
            (reset! done false)))
        (count [n]
          (do (println (if (> @size 0)
                         (apply str name " " (percentage @progress @size))
                         (apply str name " " (str @progress))))
              (swap! progress (partial + n))
              @continue))
        (end []
          (if (> @size 0) (println (apply str name " 100,00%")))
          (println " ")
          (println (apply str name " Done"))
          (println " ")
          (reset! done true)))
     [operation source destination size done progress continue]]))


(defn new-identity
  [agent rsa-path]
  (add-identity agent
                {:private-key-path rsa-path}))

(defn new-session
  [agent ip username port]
  (session agent ip
           {:strict-host-key-checking :no :username username :port port}))

(defn state-wrap
  [state]
  (let [[operation source destination size done progress continue] state]
    {:done @done :progress @progress}))

(defn sftp-put
  [size progress channel input name]
  (let [[monitor state] (sftp-monitor name size)]
    (try
      (sftp channel {:mode :resume :with-monitor monitor}
           :put input name)
      (catch Exception e
        (let [potential-progress (get-in (state-wrap state) [:progress])]
          (if (< @progress potential-progress) (reset! progress potential-progress)))
        (println (apply str "Wysypało sie bo: " (.getMessage e)))
        (throw (ex-info (.getMessage e) {:progress @progress}))))
    (reset! progress (get-in (state-wrap state) [:progress]))
    (state-wrap state)))



(defn upload-to-sftp
  [progress host port input size name destination username rsa-path]
  (println "Starting...")
  (let [agent (ssh-agent {:use-system-ssh-agent false})]
    (new-identity agent rsa-path)
    (let [session (new-session agent host username port)]
      (try
        (my-with-connection session 5000
                            (let [channel (ssh-sftp session)]
                              (with-channel-connection channel
                                                       (sftp channel {} :cd destination)
                                                       (sftp-put size progress channel input name))))
        (catch Exception e
          (throw (ex-info (.getMessage e) {:progress @progress})))))))