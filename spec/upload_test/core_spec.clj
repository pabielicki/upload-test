(ns upload-test.core-spec
  (:require [speclj.core :refer :all]
            [upload-test.core :refer :all]
            [upload-test.retries :refer :all]))
(def success-mock
  [5 5 5 5 5 0])
(def exception-mock
  [5 5 5 (Exception. "eueueue") (Exception. "eueueue") (Exception. "eueueue")  5 (Exception. "eueueue") (Exception. "eueueue") 5 5 (Exception. "eueueue") (Exception. "eueueue") 0])
(def failure-mock
  [5 (Exception. "eueueue") (Exception. "eueueue") (Exception. "eueueue") (Exception. "eueueue") (Exception. "eueueue") 5 0])

(defn mock-function
  [results]
  (let [result-atom (atom results)]
    (fn []
      (let [crnt (first @result-atom)]
        (swap! result-atom rest)
        crnt))))

(describe "circuit-breaker"
  (it "Throw exception if retrys counter exceed max retrys number"
      (should-throw Exception "eueueue"
                    (circuit-breaker (mock-function failure-mock) 3))
      )

  (it "Return 0 on success"
      (should= 0 (circuit-breaker (mock-function success-mock) 3))
      )
  (it "Reset retrys counts to 0 on progress"
      (should= 0 (circuit-breaker (mock-function exception-mock) 3))
      )
  )