(ns script.time
  (:import
   java.time.ZonedDateTime
   java.time.ZoneId
   java.time.format.DateTimeFormatter
   java.time.temporal.ChronoUnit
   ))


(def ^ZoneId TIMEZONE (ZoneId/of "Asia/Seoul"))


(defn chrono-version-str
  ([]
   (chrono-version-str "yyyy.D"))
  ([fmt]
   (let [^ZonedDateTime zdt-now (ZonedDateTime/now TIMEZONE)]
     (str (.. (DateTimeFormatter/ofPattern fmt) (format zdt-now))
          "." (.. zdt-now (truncatedTo ChronoUnit/DAYS) (until zdt-now ChronoUnit/SECONDS))))))
