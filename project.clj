(defproject net.n01se/clojure-jna "1.1.0-SNAPSHOT"
  :description "Access native libraries from Clojure"
  :url "http://github.com/Chouser/clojure-jna/"
  :scm {:name "git"
        :url "https://github.com/Chouser/clojure-jna/"}
  :dependencies [[net.java.dev.jna/jna "4.0.0"]]
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :min-lein-version "2.0.0"
  :jvm-opts ["-Djna.nosys=true"]
  :java-source-paths ["src/java"]
  :profiles {:1.2 {:dependencies [[org.clojure/clojure "1.2.0"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.0"]]}}
  :aliases {"1.2" ["with-profile" "1.2"]
            "1.3" ["with-profile" "1.3"]
            "1.4" ["with-profile" "1.4"]
            "1.5" ["with-profile" "1.5"]})
