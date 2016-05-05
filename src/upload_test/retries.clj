(ns upload-test.retries)

(defn check-it
  [result max-retries retries]
  (if (= (type result) Exception)
    (if (< max-retries retries)
      (throw result)
      2)
    (if (< 0 result)
      1
      0)))

(defn circuit-breaker
  [f max-retries]
  (loop [retries 0]
    (let [result (check-it (f) max-retries retries)]
      (if (= 0 result)
        0
        (if (= 1 result)
          (do (prn "Reset retries") (recur 0))
          (do (prn "Retry") (recur (inc retries))))))))

(defn circuit-breaker
  [f max-retries]
  (if (= (type (f)) Exception)
    "eueueueuue"))