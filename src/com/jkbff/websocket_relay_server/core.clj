(ns com.jkbff.websocket-relay-server.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [org.httpkit.server :refer [run-server]]
            [com.jkbff.websocket-relay-server.helper :as helper]
            [com.jkbff.websocket-relay-server.middleware :as middleware]
            [org.httpkit.server :as http-kit]
            [clojure.data.json :as json]))

(defonce channels (atom {}))

(defn send-json
	[channel data]
	(http-kit/send! channel (json/write-str data :key-fn #(helper/entities-fn (name %)))))

(defn notify-clients
	[group msg source-channel]
	(if (= group "echo")
		(send-json source-channel msg)

		(let [channels (disj (get @channels group #{}) source-channel)]
			(doseq [listener channels]
				(send-json listener msg)))))

(defn add-listener
	[old channel group]
	(assoc old group (conj (get old group #{}) channel)))

(defn remove-listener
	[old channel]
	(zipmap (keys old) (map #(disj % channel) (vals old))))

(defn connect!
	[channel group]
	(println (str "channel " channel " connecting to group '" group "'"))
	(swap! channels add-listener channel group)
	(notify-clients group {:type "joined" :payload (.toString channel) :created-at (quot (System/currentTimeMillis) 1000)} channel))

(defn disconnect!
	[channel group status]
	(println (str "channel " channel " closed: " status))
	(swap! channels remove-listener channel)
	(notify-clients group {:type "left" :payload (.toString channel) :created-at (quot (System/currentTimeMillis) 1000)} channel))

(defn receive-message
	[channel group message]
	(println (str "publishing to group '" group "' with message: " message))
	(notify-clients group {:type "message" :payload message :created-at (quot (System/currentTimeMillis) 1000)} channel))

;{:sender {:char_id 0 :name ... player info}
; :message ""
; :channel ""
;}

(defn subscribe-handler
	[request group]
	(http-kit/with-channel request channel
						   (connect! channel group)
						   (http-kit/on-close channel (partial disconnect! channel group))
						   (http-kit/on-receive channel (partial receive-message channel group))
						   ))

(defn publish-handler
	[group message]
	(println (str "publishing to '" group "' with message: " message))
	(notify-clients group {:type "message" :payload message :created-at (quot (System/currentTimeMillis) 1000)} nil)
	{:status 204})

(defroutes open-routes
		   (GET "/subscribe/:group" [group :as request] (subscribe-handler request (clojure.string/lower-case group)))
			 (GET "/status" [] {:body {:message "OK"}})
		   ;(POST "/publish/:group" [group :as {message :body}] (publish-handler group message))
		   )

(defroutes unknown-route
           (route/not-found {:body {:message "Not Found"}}))

(def app (-> (routes
				 open-routes
				 unknown-route)
			 (wrap-json-response {:key-fn #(helper/entities-fn (name %))})
			 middleware/trim-trailing-slash
			 ;middleware/set-text-content-type
			 ;(wrap-json-body {:keywords? #(keyword (helper/identifiers-fn %))})
			 (wrap-defaults api-defaults)
			 ;middleware/log-request-and-response
			 ))

(defn -main
	[& args]
	(let [port 80]
		(run-server app {:port port})
		(println (str "Server started on port " port))))
