(ns ib-re-actor-976-plus.test.mapping
  "Facts about the mapping layer.

  Field names come from the generated mappings, which follow the Java API
  closely (:lmt-price, :total-quantity, :sec-type), not the older hand-written
  names kept in mapping-legacy.

  The point of these facts is not to enumerate every field - the generator
  handles those - but to pin down the behaviour that regressions actually hit:
  round-tripping, enum translation, and ->map support for the read-only classes
  IB hands back through callbacks."
  (:require
   [ib-re-actor-976-plus.mapping :as m :refer [->map map->]]
   [midje.sweet :refer [contains fact tabular]])
  (:import
   (com.ib.client Bar Contract ContractDetails Decimal Execution ExecutionFilter
                  HistoricalSession HistoricalTick HistoricalTickBidAsk
                  HistoricalTickLast Order SoftDollarTier)))

(defn round-trip
  "map -> Java object -> map, which is what most of these facts check."
  [type m]
  (->map (map-> type m)))

;;;
;;; Contract
;;;

(def a-contract
  {:conid 1
   :symbol "AAPL"
   :sec-type :equity
   :exchange "SMART"
   :currency "USD"
   :strike 18.0
   :right :put
   :multiplier 234.567
   :local-symbol "AAPL"
   :trading-class "NMS"
   :sec-id-type :isin
   :sec-id "US0378331005"})

(fact "a contract round-trips, translating its enum fields"
      (round-trip Contract a-contract) => (contains a-contract))

(fact "enum fields come back as keywords, not strings or enums"
      (:sec-type (round-trip Contract a-contract)) => :equity
      (:right (round-trip Contract a-contract)) => :put
      (:sec-id-type (round-trip Contract a-contract)) => :isin)

(fact "the multiplier is a string in the API but a number here"
      (:multiplier (round-trip Contract a-contract)) => 234.567)

;;;
;;; Order
;;;

(def an-order
  {:action :buy
   :order-type :limit
   :lmt-price 23.99
   :aux-price 24.99
   :tif :day
   :transmit false
   :all-or-none true
   :outside-rth true
   :discretionary-amt 1.99
   :account "SOME ACCOUNT"
   ;; added in 10.45
   :hedge-max-size 5})

(fact "an order round-trips"
      (round-trip Order an-order) => (contains an-order))

(fact "quantities are carried as IB Decimals"
      (str (:total-quantity (round-trip Order (assoc an-order :total-quantity 100))))
      => "100")

;;;
;;; ContractDetails and ExecutionFilter
;;;

(fact "contract details round-trip, including :settlement-method added in 10.49"
      (round-trip ContractDetails {:market-name "NQ"
                                   :min-tick 0.25
                                   :long-name "E-mini NASDAQ 100"
                                   :contract-month "202612"
                                   :time-zone-id "US/Central"
                                   :settlement-method "PHYS"})
      => (contains {:market-name "NQ"
                    :min-tick 0.25
                    :long-name "E-mini NASDAQ 100"
                    :contract-month "202612"
                    :time-zone-id "US/Central"
                    :settlement-method "PHYS"}))

(fact "an execution filter round-trips - note IB stores its sec-type as a string"
      (round-trip ExecutionFilter {:client-id 1
                                   :acct-code "SOME ACCOUNT"
                                   :symbol "ES"
                                   :sec-type "FUT"
                                   :exchange "GLOBEX"
                                   :side "BUY"})
      => (contains {:client-id 1
                    :acct-code "SOME ACCOUNT"
                    :symbol "ES"
                    :sec-type "FUT"
                    :exchange "GLOBEX"
                    :side "BUY"}))

(fact "an execution round-trips"
      (round-trip Execution {:exec-id "some execution id"
                             :order-id 4
                             :client-id 1
                             :acct-number "SOME ACCOUNT"
                             :exchange "GLOBEX"
                             :side "BOT"
                             :price 6.78
                             :perm-id 5
                             :avg-price 23.45})
      => (contains {:exec-id "some execution id"
                    :order-id 4
                    :client-id 1
                    :acct-number "SOME ACCOUNT"
                    :exchange "GLOBEX"
                    :side "BOT"
                    :price 6.78
                    :perm-id 5
                    :avg-price 23.45}))

;;;
;;; Read-only classes. These arrive through EWrapper callbacks, have no setters
;;; and no public no-arg constructor, so the generator skips them and they are
;;; hand-written in mapping.clj. They have been silently missing before.
;;;

(tabular
 (fact "callback-delivered classes support ->map"
       (extends? m/Mappable ?class) => true)
 ?class
 Bar
 HistoricalTick
 HistoricalTickBidAsk
 HistoricalTickLast
 HistoricalSession
 SoftDollarTier)

(fact "a Bar maps to a Clojure map, converting its Decimals"
      (->map (Bar. "20260801" 1.0 2.0 0.5 1.5 (Decimal/get 10) 3 (Decimal/get 12)))
      => {:time "20260801" :open 1.0 :high 2.0 :low 0.5 :close 1.5
          :volume 10 :count 3 :wap 12.0})

(fact "a HistoricalSession maps to a Clojure map"
      (->map (HistoricalSession. "20260801:0930" "20260801:1600" "20260801"))
      => {:start-date-time "20260801:0930"
          :end-date-time "20260801:1600"
          :ref-date "20260801"})

(fact "a HistoricalTick maps to a Clojure map"
      (->map (HistoricalTick. 1000000000 2.5 (Decimal/get 7)))
      => {:time 1000000000 :price 2.5 :size 7.0})