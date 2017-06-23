(defproject umlaut "0.1.12-SNAPSHOT"
  :description "A Clojure program that receives a schema and outputs code."
  :url "https://github.com/workco/umlaut"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clojure-future-spec "1.9.0-alpha14"]
                 [camel-snake-kebab "0.4.0"]
                 [instaparse "1.4.7"]]
  :plugins [[lein-umlaut "0.1.0-SNAPSHOT"]
            [lein-cljfmt "0.5.6"]
			[lein-kibit "0.1.5"]]
  :main umlaut.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {
                   :dependencies
                   [[org.clojure/test.check "0.9.0"]]}})
