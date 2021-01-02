(ns com.jkbff.websocket-relay-server.middleware
	(:require [ring.util.response :as response]
						[com.jkbff.websocket-relay-server.helper :as helper]
						[ring.middleware.json :refer [wrap-json-response wrap-json-body]]))

(defn set-content-type
	[handler]
	(fn [request]
		(let [response (handler request)]
			(if (contains? (:headers response) "Content-Type")
				response
				(response/content-type response "text/plain; charset=utf-8")))))

(defn trim-trailing-slash
	[handler]
	(fn [request]
		(let [uri (:uri request)
			  last-letter (last uri)]
			(if (= \/ last-letter)
				(handler (assoc request :uri (subs uri 0 (dec (count uri)))))
				(handler request)))))

(defn log-request-and-response
	[handler]
	(fn [request]
		(prn "request:" request)
		(let [response (handler request)]
			(prn "response:" response)
			response)))
