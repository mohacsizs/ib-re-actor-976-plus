(ns ib-re-actor-976-plus.test.synchronous
  "Facts about the guard that stops the synchronous wrappers blocking forever.

  Everything else in that namespace needs a live TWS, so it is not tested here."
  (:require
   [ib-re-actor-976-plus.gateway :as g]
   [ib-re-actor-976-plus.synchronous :as sync :refer [await-result]]
   [midje.sweet :refer [fact throws]]))

(def a-connection
  "await-result only ever asks the connection whether it is up, which the facts
  below stub out, so it does not need to be a real one."
  {:ecs ::fake})

(fact "a delivered promise is returned as-is"
      (with-redefs [g/is-connected? (constantly true)]
        (await-result a-connection (doto (promise) (deliver {:type :current-time}))))
      => {:type :current-time})

(fact "a disconnected connection fails immediately rather than blocking"
      (with-redefs [g/is-connected? (constantly false)]
        (await-result a-connection (promise)))
      => (throws clojure.lang.ExceptionInfo #"Not connected"))

(fact "a connection that never answers times out instead of blocking forever"
      (with-redefs [g/is-connected? (constantly true)]
        (binding [sync/*timeout-ms* 50]
          (await-result a-connection (promise))))
      => (throws clojure.lang.ExceptionInfo #"did not respond within 50ms"))

(fact "the timeout is reported in ex-data so callers can react to it"
      (try
        (with-redefs [g/is-connected? (constantly true)]
          (binding [sync/*timeout-ms* 50]
            (await-result a-connection (promise))))
        (catch clojure.lang.ExceptionInfo e (ex-data e)))
      => {:type :timeout :timeout-ms 50})

(fact "a request that answers within the timeout still works"
      (with-redefs [g/is-connected? (constantly true)]
        (binding [sync/*timeout-ms* 5000]
          (let [result (promise)]
            (future (Thread/sleep 20) (deliver result :answered))
            (await-result a-connection result))))
      => :answered)
