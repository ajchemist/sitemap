(ns script.build
  (:require
   [clojure.java.io :as jio]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.reader :as deps.reader]
   [user.java.io.alpha :as u.jio]
   [user.tools.deps.maven.alpha :as maven]
   [user.tools.deps.clean :as clean]
   [user.tools.deps.jar :as jar]
   [user.tools.deps.install :as install]
   [user.tools.deps.deploy :as deploy]
   [script.time :refer [chrono-version-str]]
   ))


(def library 'ajchemist/sitemap)


;;


(defn read-prompt
  ^String
  [prompt]
  (print prompt)
  (flush)
  (read-line))


(defn read-password
  ^String
  [^String fmt & args]
  (String/valueOf
    (. (System/console) (readPassword fmt (into-array Object args)))))


;;


(defn build-artifacts
  [library version]
  (clean/clean "target")
  (let [pom-file (maven/sync-pom library {:mvn/version version})
        jarpath  (jar/maven-jar (str pom-file) nil nil {:jarname "package.jar"})]
    (println "Created jar:" jarpath)
    #_(println
        (install/install nil nil jarpath (str pom-file)))
    (println
      (str "\n- " version "\n"))
    [{:file-path (str pom-file) :extension "pom"}
     {:file-path jarpath :extension "jar"}]))


(defn deploy
  [artifacts]
  (println
    (deploy/deploy
      nil nil
      artifacts
      ["clojars" {:url "https://clojars.org/repo/"}]
      {:credentials
       {:username (or (System/getenv "CLOJARS_USERNAME") (read-prompt "CLOJARS_USERNAME"))
        :password (or (System/getenv "CLOJARS_PASSWORD") (read-password "%s" "CLOJARS_PASSWORD"))}
       :allow-unsigned? true})))


(defn -main
  [& xs]
  (try
    (build-artifacts library (chrono-version-str))
    (catch Throwable e
      (.printStackTrace e)
      (System/exit 127))
    (finally
      (shutdown-agents)
      (System/exit 0))))


(comment

  (deploy (build-artifacts library (chrono-version-str)))
  )
