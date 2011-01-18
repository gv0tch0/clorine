(defproject com.relaynetwork/clorine "1.0.0"
  :description "Clorine"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :jvm-opts ["-Xmx512M"]
  :dev-dependencies [[swank-clojure "1.2.1"]]
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [commons-dbcp/commons-dbcp "1.4"]]
)
