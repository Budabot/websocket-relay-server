(defproject websocket-relay-server "1.0"
	:description "Websocket Relay Server"
	:url "http://example.com/FIXME"
	:license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
			:url "https://www.eclipse.org/legal/epl-2.0/"}
	:dependencies [[org.clojure/clojure "1.10.1"]
				   [http-kit "2.5.0"]
				   [compojure "1.6.1"]
				   [ring/ring-defaults "0.3.2"]
				   [ring/ring-json "0.4.0"]
				   [org.clojure/data.json "0.2.6"]
				   [jarohen/chime "0.3.2"]
				   [org.clojure/tools.logging "1.1.0"]
				   [log4j "1.2.17"]]

	:main com.jkbff.websocket-relay-server.core
	:repl-options {:init-ns com.jkbff.websocket-relay-server.core})
