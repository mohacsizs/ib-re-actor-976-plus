(ns ib-re-actor-976-plus.test.translation
  "Facts about translation.clj. These cover the tables the rest of the library
  leans on, not every entry in them.

  Note that since 10.x most tables translate to typed IB enums rather than to
  strings, and that `:from-ib` on those tables expects the enum back, not the
  string IB puts on the wire."
  (:require
   [ib-re-actor-976-plus.translation :as t :refer [translate valid?]]
   [midje.sweet :refer [fact tabular throws]])
  (:import
   (com.ib.client OrderType Types$Action Types$Right Types$SecIdType Types$SecType
                  Types$TimeInForce Types$WhatToShow)))

;;;
;;; Table basics
;;;

(fact "unknown string codes just translate into themselves"
      (translate :from-ib :security-type "some weird value") => "some weird value"
      (translate :to-ib :security-type "some weird value") => "some weird value")

(fact "unknown keyword codes throw"
      (translate :to-ib :security-type :I-misspelled-something)
      => (throws #"^Can't translate to IB"))

(fact "nil translates to nil in both directions"
      (translate :to-ib :security-type nil) => nil
      (translate :from-ib :security-type nil) => nil)

;;;
;;; Enums
;;;

(tabular
 (fact "security types round-trip through the IB enum"
       (translate :to-ib :security-type ?keyword) => ?enum
       (translate :from-ib :security-type ?enum) => ?keyword)
 ?keyword       ?enum
 :equity        Types$SecType/STK
 :option        Types$SecType/OPT
 :future        Types$SecType/FUT
 :future-option Types$SecType/FOP
 :index         Types$SecType/IND
 :cash          Types$SecType/CASH
 :bag           Types$SecType/BAG
 :crypto        Types$SecType/CRYPTO)

(tabular
 (fact "orders, rights and security ids round-trip through their enums"
       (translate :to-ib ?table ?keyword) => ?enum
       (translate :from-ib ?table ?enum) => ?keyword)
 ?table             ?keyword            ?enum
 :order-action      :buy                Types$Action/BUY
 :order-action      :sell               Types$Action/SELL
 :order-action      :sell-short         Types$Action/SSHORT
 :order-type        :limit              OrderType/LMT
 :order-type        :market             OrderType/MKT
 :order-type        :midprice           OrderType/MIDPRICE
 :time-in-force     :day                Types$TimeInForce/DAY
 :time-in-force     :good-to-close      Types$TimeInForce/GTC
 :time-in-force     :immediate-or-cancel Types$TimeInForce/IOC
 ;; regression: :day-till-cancelled used to map to FOK
 :time-in-force     :day-till-cancelled Types$TimeInForce/DTC
 :right             :put                Types$Right/Put
 :right             :call               Types$Right/Call
 :security-id-type  :isin               Types$SecIdType/ISIN
 :security-id-type  :cusip              Types$SecIdType/CUSIP
 :what-to-show      :trades             Types$WhatToShow/TRADES
 :what-to-show      :midpoint           Types$WhatToShow/MIDPOINT
 :what-to-show      :schedule           Types$WhatToShow/SCHEDULE
 :what-to-show      :agg-trades         Types$WhatToShow/AGGTRADES)

;;;
;;; Durations and bar sizes
;;;

(tabular
 (fact "it can translate to IB durations"
       (translate :to-ib :duration [?value ?unit]) => ?expected)
 ?value ?unit    ?expected
 1      :second  "1 S"
 5      :seconds "5 S"
 5      :days    "5 D"
 1      :week    "1 W"
 1      :year    "1 Y")

(tabular
 (fact "it can translate bar sizes"
       (translate :to-ib :bar-size [?value ?unit]) => ?expected)
 ?value ?unit    ?expected
 1      :second  "1 secs"
 5      :seconds "5 secs"
 3      :minutes "3 mins"
 1      :hour    "1 hour"
 2      :days    "2 days"
 1      :week    "1 W")

;;;
;;; Tick types
;;;

(tabular
 (fact "tick field codes round-trip"
       (translate :to-ib :tick-field-code ?keyword) => ?code
       (translate :from-ib :tick-field-code ?code) => ?keyword)
 ?keyword          ?code
 :bid-size         0
 :bid-price        1
 :ask-price        2
 :ask-size         3
 ;; added in 10.46
 :odd-lot-bid      105
 :odd-lot-ask      106
 :odd-lot-ask-exch 110)

(fact "a tick list is sent to IB as a comma-separated string of codes"
      (translate :to-ib :tick-list [:shortable :odd-lots]) => "46,787")

(fact "fundamentals were de-supported by IB in 10.47"
      (valid? :to-ib :tick-field-code :fundamental-ratios) => false
      (valid? :to-ib :generic-tick-type :fundamental-ratios) => false)

;;;
;;; Account values, including the $LEDGER- prefix added in 10.47
;;;

(fact "account value keys translate to kebab-case keywords"
      (translate :from-ib :account-value-key "NetLiquidation") => :net-liquidation
      (translate :from-ib :account-value-key "AccountReady") => :account-ready)

(fact "per-currency values keep their identity in the :ledger namespace"
      (translate :from-ib :account-value-key "$LEDGER-NetLiquidation")
      => :ledger/net-liquidation
      (translate :from-ib :account-value-key "$LEDGER-CashBalance")
      => :ledger/cash-balance)

(fact "an unknown key survives the prefix rather than being dropped"
      (translate :from-ib :account-value-key "$LEDGER-SomethingNew")
      => "$LEDGER-SomethingNew"
      (translate :from-ib :account-value-key "SomethingNew") => "SomethingNew")

(tabular
 (fact "value-type predicates accept both plain and $LEDGER- forms"
       (?predicate ?key) => ?expected)
 ?predicate               ?key                         ?expected
 t/numeric-account-value? :net-liquidation             true
 t/numeric-account-value? :ledger/net-liquidation      true
 t/numeric-account-value? :ledger/cash-balance         true
 t/numeric-account-value? :account-ready               false
 t/integer-account-value? :day-trades-remaining        true
 t/integer-account-value? :ledger/day-trades-remaining true
 t/boolean-account-value? :account-ready               true
 t/boolean-account-value? :ledger/account-ready        true
 t/boolean-account-value? :net-liquidation             false)
