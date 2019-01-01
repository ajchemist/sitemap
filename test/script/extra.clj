(ns script.extra
  (:require
   [clojure.java.io :as jio]
   [clojure.tools.cli :as cli]
   [user.apache.maven.pom.alpha :as pom]
   ))


;; * cli options


(def cli-options
  [["-h" "--help"]])


;; * main


(def cmds
  {"get-version" (fn [] (println (.getVersion (pom/read-pom "pom.xml"))))})


(defn -main
  [& xs]
  (loop [args (drop-while (fn [cmd] (not (find cmds cmd))) xs)]
    (when-not (empty? args)
      (let [[cmd] args
            cmdfn (get cmds cmd)
            args  (rest args)]
        (when (fn? cmdfn)
          (cmdfn))
        (recur (drop-while (fn [cmd] (not (find cmds cmd))) args))))))


(comment
  (cli/parse-opts ["-v" "get-version" "-h"] cli-options)


  (cli/parse-opts ["--" "get-version" "-h"] cli-options :in-order false)


  (-main "get-version")


  (-main "a")
  )
