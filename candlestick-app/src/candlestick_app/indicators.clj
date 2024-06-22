(ns candlestick-app.indicators)

(defn calculate-sma [data period]
  ;; Logic to calculate SMA
  (let [closes (map :close data)]
    (map (fn [i]
           (let [sma (->> closes
                          (drop (- i period))
                          (take period)
                          (reduce +)
                          (/ period))]
             {:date (get-in data [i :date])
              :value sma}))
         (range (dec period) (count closes)))))

(defn calculate-ema [data period]
  ;; Logic to calculate EMA
  (let [k (/ 2.0 (inc period))
        closes (map :close data)]
    (loop [i 0
           ema (first closes)
           result []]
      (if (< i (count closes))
        (let [price (nth closes i)
              ema' (+ (* k (- price ema)) ema)]
          (recur (inc i) ema' (conj result {:date (get-in data [i :date]) :value ema'})))
        result))))
