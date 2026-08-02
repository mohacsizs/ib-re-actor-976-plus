(ns ib-re-actor-976-plus.test.wrapper
  "Facts about the EWrapper reification.

  The wrapper is generated from resources/EWrapper_<version>.java at load time,
  so these facts check the shape it produces rather than any hand-written list
  of callbacks: every callback becomes a flat map of its Java parameters, keyed
  by kebab-cased parameter name, plus a :type of the kebab-cased method name.
  No translation happens here - that is the caller's job."
  (:require
   [ib-re-actor-976-plus.wrapper :as w :refer [camel-to-kebab create error?
                                               matching-message? request-end?
                                               warning?]]
   [midje.sweet :refer [contains fact tabular truthy falsey]])
  (:import
   (com.ib.client Decimal TickAttrib)))

(defn capture
  "Applies f to a freshly created wrapper and returns the messages it dispatched."
  [f]
  (let [messages (atom [])]
    (f (create #(swap! messages conj %)))
    @messages))

(defn message [f] (first (capture f)))

;;;
;;; Method names and parameters
;;;

(tabular
 (fact "Java names become kebab-case keys"
       (camel-to-kebab ?java) => ?clojure)
 ?java              ?clojure
 "currentTime"      "current-time"
 "tickSnapshotEnd"  "tick-snapshot-end"
 "reqId"            "req-id"
 "tickerId"         "ticker-id"
 "advancedOrderRejectJson" "advanced-order-reject-json")

(fact "every EWrapper method except the error overloads is reified"
      (count w/non-error-methods) => (partial < 100)
      (some #(= "error" (:name %)) w/non-error-methods) => falsey)

;;;
;;; Callbacks
;;;

(fact "a callback with no arguments dispatches just its type"
      (message #(.positionEnd %)) => {:type :position-end}
      (message #(.openOrderEnd %)) => {:type :open-order-end}
      (message #(.connectionClosed %)) => {:type :connection-closed})

(fact "arguments are carried through untranslated, under kebab-case keys"
      (message #(.currentTime % 1000000000))
      => {:type :current-time :time 1000000000}

      (message #(.nextValidId % 42))
      => {:type :next-valid-id :order-id 42}

      (message #(.contractDetailsEnd % 7))
      => {:type :contract-details-end :req-id 7}

      (message #(.historicalDataEnd % 3 "20260801" "20260802"))
      => {:type :historical-data-end :req-id 3
          :start-date-str "20260801" :end-date-str "20260802"}

      (message #(.managedAccounts % "DU111,DU222"))
      => {:type :managed-accounts :accounts-list "DU111,DU222"})

(fact "account values arrive as the raw strings IB sent"
      (message #(.updateAccountValue % "CashBalance" "123.456" "USD" "DU111"))
      => {:type :update-account-value :key "CashBalance" :value "123.456"
          :currency "USD" :account-name "DU111"})

(fact "ticks keep their numeric field code and IB objects"
      (message #(.tickPrice % 42 2 3.0 (TickAttrib.)))
      => (contains {:type :tick-price :ticker-id 42 :field 2 :price 3.0})

      (message #(.tickSize % 42 3 (Decimal/get 4)))
      => (contains {:type :tick-size :ticker-id 42 :field 3}))

;;;
;;; Error overloads - these are hand-written because they are overloaded
;;;

(fact "the request-specific error carries the full context"
      (message #(.error % 42 1785704597886 502 "Couldn't connect" nil))
      => {:type :error :id 42 :time 1785704597886 :code 502
          :message "Couldn't connect" :advanced-order-reject-json nil})

(fact "the string error overload carries only a message"
      (message #(.error % "some message")) => {:type :error :message "some message"})

(fact "the exception overload carries the exception itself"
      (let [ex (Exception. "some problem")]
        (message #(.error % ex)) => {:type :error :ex ex}))

;;;
;;; Classifying and routing messages
;;;

(tabular
 (fact "errors in the 2100-2200 range, and anything IB labels a warning, are warnings"
       (warning? ?message) => ?warning
       (error? ?message) => ?error)
 ?message                                              ?warning ?error
 {:type :error :code 2104}                             truthy   falsey
 {:type :error :code 2200}                             truthy   falsey
 {:type :error :code 502}                              falsey   truthy
 {:type :error :code 10000}                            falsey   truthy
 ;; IB sends some warnings with an error code but a labelled message
 {:type :error :code 399 :message "Warning: outside RTH"} truthy falsey
 {:type :tick-price :ticker-id 1}                      falsey   falsey)

(tabular
 (fact "a message matches a subscription by type and by whichever id it carries"
       (matching-message? :tick-price ?id ?message) => ?expected)
 ?id ?message                                  ?expected
 42  {:type :tick-price :ticker-id 42}         truthy
 43  {:type :tick-price :ticker-id 42}         falsey
 nil {:type :tick-price :ticker-id 42}         truthy
 42  {:type :tick-size :ticker-id 42}          falsey
 7   {:type :tick-price :req-id 7}             truthy
 7   {:type :tick-price :order-id 7}           truthy)

(tabular
 (fact "end messages close out the request they belong to"
       (request-end? ?type ?id ?message) => ?expected)
 ?type              ?id ?message                                ?expected
 :contract-details  7   {:type :contract-details-end :req-id 7} truthy
 :contract-details  8   {:type :contract-details-end :req-id 7} falsey
 :position          nil {:type :position-end}                   truthy
 :price-bar         3   {:type :price-bar-complete :req-id 3}   truthy
 :contract-details  7   {:type :contract-details :req-id 7}     falsey)