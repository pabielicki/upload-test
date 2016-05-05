(ns upload-test.byte-chunk
  (:use  byte-streams
         clojure.java.io)
  (:require [clojure.java.io :as io])
  (:import (java.io InputStream OutputStream
                  FileInputStream FileOutputStream)))

(set! *warn-on-reflection* true)


;

(deftype ByteArrayChunk [^bytes array ^int offset ^int end]
  clojure.lang.IChunk
  (dropFirst [this]
    (if (= offset end)
      (throw (IllegalStateException. "dropFirst of empty ByteArrayChunk"))
      (ByteArrayChunk. array (inc offset) end)))
  (reduce [this f start]
    (loop [ret (f start (aget array offset))
           i (inc offset)]
      (if (< i end)
        (recur (f ret (aget array i)) (inc i))
        ret)))
  clojure.lang.Indexed
  (nth [this i]
    (aget array (+ offset i)))
  (nth [this i not-found]
    (if (and (>= i 0) (< i (.count this)))
      (.nth this i)
      not-found))
  clojure.lang.Counted
  (count [this]
    (- end offset)))

(defn byte-array-chunk
  ([array]
     (ByteArrayChunk. array 0 (count array)))
  ([array offset]
     (ByteArrayChunk. array offset (count array)))
  ([array offset end]
     (ByteArrayChunk. array offset end)))



(defn write-to-chunks
  [file ^OutputStream stream start size]
  (.write stream
          (.array (byte-array-chunk (byte-streams/to-byte-array
                                      (io/file file))))
          start
          size
          )
  )

(defn chunk-size
  [file chunks-count]
  (int (Math/ceil (/ (file-size file) chunks-count))))

(defn next-chunk-length
  [pos file chunks-count file-chunk-size]
  (if (= pos (- chunks-count 1))
    (- (file-size file) (* (- chunks-count 1) file-chunk-size))
    file-chunk-size))

(defn file-name
  [var]
  (apply str "tmp-" (str var)))

(defn file-name-list
  [chunks-count]
  (map file-name (range chunks-count)))

(defn destroy-file
  [file]
  (io/delete-file file))

(defn chunker
  [file chunks-count]
    (let [file-chunk-size (chunk-size file chunks-count)]
     (doseq [j (range chunks-count)]
       (with-open [out (output-stream (file-name j))]
         (write-to-chunks
           file
           out
           (* j file-chunk-size)
           (next-chunk-length j file chunks-count file-chunk-size)))
       )
     (file-name-list chunks-count))
  )