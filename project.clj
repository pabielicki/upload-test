(defproject upload-test "0.4.4"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-ssh "0.5.14"]
                 [byte-streams "0.2.2"]
                 [listora/again "0.1.0"]
                 [antler/commons-io "2.2.0"]
                 [org.clojure/tools.cli "0.2.4"]]
  :profiles {:dev {:dependencies [[ring/ring-devel "1.4.0"]
                                  [speclj "3.3.0"]]}}
  :plugins [[speclj "3.3.0"]
            [lein-cljsbuild "1.0.3"]]
  :main upload-test.core
  :aot [upload-test.core]
  :test-paths ["spec"]
  :cljsbuild {:builds        {:dev  {:source-paths ["src/cljs" "spec/cljs"]
                                     :compiler     {:output-to "/target"}
                                     :notify-command ["phantomjs" "bin/speclj" "/target"]}
                              :prod {:source-paths  ["src/cljs"]
                                     :compiler      {:output-to "/target"
                                                     :optimizations :simple}}}
              :test-commands {"test" ["phantomjs"  "bin/speclj" "/target"]}}
  )

