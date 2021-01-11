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

(defonce groups (atom {}))

(defn send-json
	[websocket data]
	(http-kit/send! websocket (helper/write-json data)))

(defn notify-clients
	[group msg websocket]
	(if (= group "echo")
		(send-json websocket msg)

		(let [websockets (disj (get @groups group #{}) websocket)]
			(doseq [listener websockets]
				(send-json listener msg)))))

(defn add-listener
	[old websocket group]
	(assoc old group (conj (get old group #{}) websocket)))

(defn remove-listener
	[old websocket]
	(zipmap (keys old) (map #(disj % websocket) (vals old))))

(defn connect!
	[websocket group]
	(println (str "websocket " websocket " connecting to group '" group "'"))
	(swap! groups add-listener websocket group)
	(notify-clients group {:type "joined" :payload (.toString websocket) :created-at (quot (System/currentTimeMillis) 1000)} websocket))

(defn disconnect!
	[websocket group status]
	(println (str "websocket " websocket " closed: " status))
	(swap! groups remove-listener websocket)
	(notify-clients group {:type "left" :payload (.toString websocket) :created-at (quot (System/currentTimeMillis) 1000)} websocket))

(defn receive-message
	[websocket group message]
	(let [obj (helper/read-json message)]
		(case (:type obj)
			"message" (do
									(println (str "publishing to group '" group "' with message: " message))
									(notify-clients group (assoc obj :created-at (quot (System/currentTimeMillis) 1000)) websocket))
			nil)))

(defn subscribe-handler
	[request group]
	(http-kit/with-channel request websocket
						   (connect! websocket group)
						   (http-kit/on-close websocket (partial disconnect! websocket group))
						   (http-kit/on-receive websocket (partial receive-message websocket group))
						   ))

(defn publish-handler
	[group message]
	(println (str "publishing to '" group "' with message: " message))
	(notify-clients group {:type "message" :payload message :created-at (quot (System/currentTimeMillis) 1000)} nil)
	{:status 204})

(defn send-pings
	[]
	(let [msg {:type "ping" :payload "webocket-relay-server" :created-at (quot (System/currentTimeMillis) 1000)}]
		(doseq [websockets (vals @groups)
				websocket websockets]
			(send-json websocket msg))))

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
