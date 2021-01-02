(ns com.jkbff.websocket-relay-server.core
	(:require [compojure.core :refer :all]
						[compojure.route :as route]
						[ring.middleware.defaults :refer [wrap-defaults api-defaults]]
						[ring.middleware.json :refer [wrap-json-response wrap-json-body]]
						[org.httpkit.server :refer [run-server]]
						[com.jkbff.websocket-relay-server.helper :as helper]
						[com.jkbff.websocket-relay-server.middleware :as middleware]
						[org.httpkit.server :as http-kit]
						[chime.core :as chime])
	(:import [java.time Instant Duration]))

(defonce channels (atom {}))

(defn send-json
	[channel data]
	(http-kit/send! channel (helper/write-json data)))

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
	(let [obj (helper/read-json message)]
		(case (:type obj)
			"message" (do
									(println (str "publishing to group '" group "' with message: " message))
									(notify-clients group (assoc obj :created-at (quot (System/currentTimeMillis) 1000)) channel))
			"ping" nil)))

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

(defn send-pings
	[]
	(let [msg {:type "ping" :payload "webocket-relay-server" :created-at (quot (System/currentTimeMillis) 1000)}]
		(doseq [groups (vals @channels)
						channel groups]
			(send-json channel msg))))

(defroutes open-routes
	(GET "/subscribe/:group" [group :as request] (subscribe-handler request (clojure.string/lower-case group)))
	(GET "/health" [] {:body {:message "OK"}})
	;(POST "/publish/:group" [group :as {message :body}] (publish-handler group message))
	)

(defroutes unknown-route
	(route/not-found {:body {:message "Not Found"}}))

(def app (-> (routes open-routes unknown-route)
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
		(println (str "Server started on port " port))
		(chime/chime-at (rest (chime/periodic-seq (Instant/now) (Duration/ofMinutes 2)))
										(fn [time]
											(send-pings)))

		))
