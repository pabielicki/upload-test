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
  []
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
            (println (apply str "Source: " src " Destination: " dest))
            (reset! operation op)
            (reset! source src)
            (reset! destination dest)
            (reset! size max)
            (reset! done false)))
        (count [n]
          (prn (if (> @size 0)
                 (percentage @progress @size)
                 @progress))
          (swap! progress (partial + n))
          @continue)
        (end []
          (if (> @size 0) (prn "100,00%"))
          (println "Done")
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
    {:done @done :progress @progress }))

(defn sftp-put
  [progress channel input name]
  (let [[monitor state] (sftp-monitor)]
    (try
      (sftp channel {:mode :resume :with-monitor monitor}
           :put input name)
      (catch Exception e
        (reset! progress (get-in (state-wrap state) [:progress]))
        (throw (ex-info (.getMessage e) (state-wrap state)))))
    (reset! progress (get-in (state-wrap state) [:progress]))
    (state-wrap state)))



(defn upload-to-sftp
  [progress host port input name destination username rsa-path]
  (println "Starting...")
  (let [agent (ssh-agent {:use-system-ssh-agent false})]
    (new-identity agent rsa-path)
    (let [session (new-session agent host username port)]
      (try
        (my-with-connection session 5000
                            (let [channel (ssh-sftp session)]
                              (with-channel-connection channel
                                                       (ssh session {:cmd (apply str "mkdir -p " destination)})
                                                       (sftp channel {} :cd destination)
                                                       (sftp-put progress channel input name))))
        (catch Exception e
          (throw (ex-info (.getMessage e) {:progress @progress})))))))
