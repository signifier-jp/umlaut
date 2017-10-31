(defproject umlaut "0.1.16"
  :description "A Clojure program that receives a schema and outputs code."
  :url "https://github.com/workco/umlaut"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clojure-future-spec "1.9.0-alpha14"]
                 [camel-snake-kebab "0.4.0"]
                 [instaparse "1.4.7"]]
  :plugins [[lein-cljfmt "0.5.6"]
            [lein-kibit "0.1.5"]]
  :deploy-repositories {"clojars" {:sign-releases false}}
  :main umlaut.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {
                   :dependencies
                   [[org.clojure/test.check "0.9.0"]]}})
