(ns com.jkbff.websocket-relay-server.core
	(:require [compojure.core :refer :all]
			  [compojure.route :as route]
			  [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
			  [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
			  [org.httpkit.server :refer [run-server]]
			  [com.jkbff.websocket-relay-server.helper :as helper]
			  [com.jkbff.websocket-relay-server.middleware :as middleware]
			  [org.httpkit.server :as http-kit]
			  [chime.core :as chime]
			  [clojure.tools.logging :as log])
	(:import [java.time Instant Duration]))

(defonce groups (atom {}))

(defrecord WS [id web-socket])

(defn send-json
	[ws data]
	(http-kit/send! (:web-socket ws) (helper/write-json data)))

(defn notify-clients
	[group msg ws]
	(log/info (str "publishing to group '" group "' with message: " msg))
	(if (= group "echo")
		(send-json ws msg)

		(let [ws-list (disj (get @groups group #{}) ws)]
			(doseq [ws ws-list]
				(send-json ws msg)))))

(defn add-listener
	[old ws group]
	(assoc old group (conj (get old group #{}) ws)))

(defn remove-listener
	[old ws]
	(zipmap (keys old) (map #(disj % ws) (vals old))))

(defn connect!
	[ws group]
	(log/info (str "web-socket " ws " connecting to group '" group "'"))
	(swap! groups add-listener ws group)
	(notify-clients group {:type "joined" :client-id (:id ws) :created-at (quot (System/currentTimeMillis) 1000)} ws)

	; notify new client of existing clients
	(let [ws-list (disj (get @groups group #{}) ws)
		  msg {:type "joined" :created-at (quot (System/currentTimeMillis) 1000)}]
		(doseq [ws-other ws-list]
			(send-json ws (assoc msg :client-id (:id ws-other))))))

(defn disconnect!
	[ws group status]
	(log/info (str "web-socket " ws " closed: " status))
	(swap! groups remove-listener ws)
	(notify-clients group {:type "left" :client-id (:id ws) :created-at (quot (System/currentTimeMillis) 1000)} ws))

(defn receive-message
	[ws group message]
	(let [obj (helper/read-json message)
		  obj (assoc obj :client-id (:id ws) :created-at (quot (System/currentTimeMillis) 1000))]
		(case (:type obj)
			"message" (notify-clients group obj ws)
			nil)))

(defn subscribe-handler
	[request group]
	(http-kit/with-channel request web-socket
						   (let [id (helper/generate-random 32)
								 ws (->WS id web-socket)]
							   (connect! ws group)
							   (http-kit/on-close web-socket (partial disconnect! ws group))
							   (http-kit/on-receive web-socket (partial receive-message ws group))
							   )))

(defn publish-handler
	[group message]
	(log/info (str "publishing to '" group "' with message: " message))
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
		(log/info (str "Server started on port " port))
		(chime/chime-at (rest (chime/periodic-seq (Instant/now) (Duration/ofMinutes 2)))
										(fn [time]
											(send-pings)))

		))
