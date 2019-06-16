(ns com.jkbff.websocket-relay-server.helper
	(:require [ring.util.response :as response]))

(defn invalid-email?
	[email]
	(not (re-matches #".+\@.+\..+" email)))

(defn missing-required-fields?
	[& fields]
	(if (some #(clojure.string/blank? (str %)) fields)
		true
		false))

(defn generate-random
	[length]
	(let [chars (seq "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
		  random-chars (repeatedly #(nth chars (rand-int (count chars))))]
		(apply str (take length random-chars))))

(defn salt-password
	[password salt]
	(str password salt))

(defn entities-fn
	[e]
	(.replace e \- \_))

(defn identifiers-fn
	[e]
	(.replace e \_ \-))

(defn serve-resource-file
	[filename content-type]
	(response/content-type (response/resource-response filename {:root "public"}) content-type))