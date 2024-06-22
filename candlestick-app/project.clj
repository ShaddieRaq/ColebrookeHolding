(defproject candlestick-app "0.1.0-SNAPSHOT"
  :description "A simple candlestick chart app"
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [compojure "1.6.2"]
                 [ring "1.9.0"]
                 [ring/ring-json "0.5.0"]
                 [ring-cors "0.1.13"]
                 [clj-http "3.12.3"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/tools.logging "1.2.4"]]
  :main ^:skip-aot candlestick-app.handler
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler candlestick-app.handler/app}
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-mock "0.4.0"]]}})
