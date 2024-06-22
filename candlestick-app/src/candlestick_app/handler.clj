(ns candlestick-app.handler
  (:require
    [compojure.core :refer [defroutes GET]]
    [compojure.route :as route]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [ring.middleware.cors :refer [wrap-cors]]
    [ring.middleware.params :refer [wrap-params]] ;; Include ring.middleware.params
    [clj-http.client :as client]
    [clojure.data.json :as json]
    [ring.adapter.jetty :refer [run-jetty]]
    [clojure.tools.logging :as log]))

(defn build-candlestick-url [pair interval start end]
  (let [base-url (str "https://api.exchange.coinbase.com/products/" pair "/candles?granularity=" interval)
        start-param (if start (str "&start=" start) "")
        end-param (if end (str "&end=" end) "")]
    (str base-url start-param end-param)))

(defn get-candlestick-data [pair interval start end]
  (let [url (build-candlestick-url pair interval start end)
        response (client/get url {:headers {"Content-Type" "application/json"}})]
    (log/info "Fetching URL: " url)
    (if (= 200 (:status response))
      (let [data (json/read-str (:body response) :key-fn keyword)]
        (log/info "Candlestick data fetched:" data)
        data)
      [])))

(defn get-trading-pairs []
  (let [response (client/get "https://api.exchange.coinbase.com/products"
                             {:headers {"Content-Type" "application/json"}})]
    (if (= 200 (:status response))
      (let [pairs (->> (json/read-str (:body response) :key-fn keyword)
                       (map #(select-keys % [:id :display_name])))]
        (log/info "Trading pairs fetched:" pairs)
        pairs)
      [])))

(defroutes app-routes
  (GET "/api/pairs" [] (fn [_] {:status 200 :body {:pairs (get-trading-pairs)}}))
  (GET "/api/candlestick/:pair/:interval" [pair interval :as {query-params :query-params}]
       (let [start (get query-params "start")
             end (get query-params "end")]
         (log/info "Received request with start:" start "and end:" end)
         (let [data (get-candlestick-data pair interval start end)]
           (log/info "Sending candlestick data for pair" pair "and interval" interval "with start" start "and end" end)
           {:status 200 :body {:candlestick-data data}})))
  (route/not-found "Not Found"))

(def app
  (wrap-cors
    (wrap-json-body
      (wrap-json-response
        (wrap-params app-routes))) ;; Wrap app-routes with wrap-params
    :access-control-allow-origin [#".*"]
    :access-control-allow-methods [:get :post :put :delete :options]
    :access-control-allow-headers ["Content-Type"]))

(defn -main [& args]
  (let [port (Integer. (or (System/getenv "PORT") "3000"))]
    (run-jetty app {:port port :join? false})))
