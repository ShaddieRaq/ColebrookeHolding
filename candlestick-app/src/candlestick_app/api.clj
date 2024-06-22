(ns candlestick-app.api
  (:require [clj-http.client :as client]
            [cheshire.core :as json]))

(defn get-candlestick-data [pair interval]
  (let [url (str "https://api.pro.coinbase.com/products/" pair "/candles")
        response (client/get url {:query-params {"granularity" interval}})
        data (json/parse-string (:body response) true)]
    (json/generate-string data)))
