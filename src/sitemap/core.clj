(ns sitemap.core
  "Library for sitemap rendering and validation."
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [rum.core :as rum]
   [sitemap.validator :as v]
   )
  (:import
   java.util.zip.GZIPInputStream
   java.util.zip.GZIPOutputStream
   ))


(def ^:dynamic *extension* ".xml")


(def xml-declaration "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")


;; Sitemaps MUST be UTF-8 encoded - http://www.sitemaps.org/faq.html#faq_output_encoding
(def encoding-utf-8 "UTF-8")


;; https://www.sitemaps.org/protocol.html
(def chunk-size 50000)


(defn need-siteindex?
  [url-entries]
  (pos? (quot (count url-entries) chunk-size)))


;;


(defn- right-unslashify
  [^String s]
  (if (str/ends-with? s "/")
    (subs s 0 (unchecked-dec (.length s)))
    s))


(defn- spit-utf8
  [path s]
  (spit path s :encoding encoding-utf-8))


(defn- spit-gzipped
  [path s]
  (with-open [w (-> path
                  (jio/output-stream)
                  (GZIPOutputStream.)
                  (jio/writer :encoding encoding-utf-8))]
    (.write w s)))


(defn gzipped?
  [path]
  (try
    (.close (GZIPInputStream. (jio/input-stream path)))
    true
    (catch java.util.zip.ZipException _ false)
    (finally)))


;;


(defn url-entry
  [{:keys [loc lastmod changefreq priority]
    :as   entry}]
  [:url
   [:loc loc]
   (when lastmod
     [:lastmod lastmod])
   (when changefreq
     [:changefreq changefreq])
   (when priority
     [:priority priority])])


(defn urlset
  [entries]
  [:urlset
   {:xmlns              "http://www.sitemaps.org/schemas/sitemap/0.9"
    ;; :xmlns:xsi          "http://www.w3.org/2001/XMLSchema-instance"
    ;; :xsi:schemaLocation "http://www.sitemaps.org/schemas/sitemap/0.9 http://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd"
    }
   (map url-entry entries)])


(defn render-sitemap
  "Render Clojure data structures to a String of sitemap XML."
  [url-entries]
  (str
    xml-declaration
    (rum/render-static-markup
      (urlset url-entries))))


(defn render-sitemap*
  "Render Clojure data structures to a seq of sitemap XMLs, chunked at the maximum sitemap size (50,000)."
  [url-entries]
  (->> url-entries
    (partition-all chunk-size)
    (map render-sitemap)))


(defn render-siteindex
  "Render sitemap index XML for the `sitemap-paths`, returned as a string."
  [sitemap-paths]
  (str
    xml-declaration
    (rum/render-static-markup
      [:sitemapindex
       {:xmlns "http://www.sitemaps.org/schemas/sitemap/0.9"}
       (map
         (fn [path] [:sitemap [:loc path]])
         sitemap-paths)])))


(defprotocol SitemapOutputPathResolver
  (index-path  [_ basename] "Returns the output path for a sitemap or sitemap index.")
  (chunk-path [_ basename i] "Returns the output path for a sitemap 'chunk' (a sub-sitemap of a sitemap index) with index i."))


(def default-output-path-resolver
  "Default output path resolver"
  (reify SitemapOutputPathResolver
    (index-path [_ basename]
      (str basename *extension*))
    (chunk-path [_ basename i]
      (str basename "-" i *extension*))))


(defn render-sitemap-and-save*
  "Render Clojure data structures to sitemap XML, emitting a sitemap index with sitemap chunks
  when the number of url-entries is greater than permitted for a single sitemap (50,000). The
  output file name(s) will be based on the basename.

  Return sitemaps and siteindex output-path record.

  Example: (render-sitemap-and-save* \"https://example.com\" \"dir/sitemap\" url-entries)
  will emit dir/sitemap.xml when count(url-entries) <= 50,000, otherwise will emit
  dir/sitemap.xml (index, pointing to https://example.com/sitemap-0.xml and so on),
  dir/sitemap-0.xml, dir/sitemap-1.xml, and so on."
  ([root-uri basename url-entries]
   (render-sitemap-and-save* root-uri basename url-entries nil))

  ([root-uri basename url-entries
    {:keys [output-path-resolver gzip?]
     :or   {output-path-resolver default-output-path-resolver
            gzip?                false}
     :as   opts}]
   (let [spit-fn (or (:spit-fn opts) (if gzip? spit-gzipped spit-utf8))]
     (binding [*extension* (if gzip? (str *extension* ".gz") *extension*)]
       (let [sitemap-xmls (render-sitemap* url-entries)
             index-path   (index-path output-path-resolver basename)]
         (jio/make-parents (jio/file index-path))
         (if (need-siteindex? url-entries)
           (let [sitemap-paths (map #(chunk-path output-path-resolver basename %) (range (count sitemap-xmls)))
                 remote-paths  (map #(str (right-unslashify root-uri) "/" (.getName (jio/file %))) sitemap-paths)]
             (spit-fn index-path (render-siteindex remote-paths))
             (run!
               (fn [[path xml]]
                 (spit-fn (doto (jio/file path) (jio/make-parents)) xml))
               (map vector sitemap-paths sitemap-xmls))
             {:siteindex index-path
              :sitemaps  sitemap-paths})
           (do
             (spit-fn index-path (first sitemap-xmls))
             {:sitemaps #{index-path}})))))))


(defn render-sitemap-and-save
  "Render Clojure data structures to a string of sitemap XML and save it to file. Does not
  check whether the number of url-entries is greater than allowed."
  [path url-entries]
  (let [sitemap-xml (render-sitemap url-entries)]
    (spit-utf8 path sitemap-xml)
    sitemap-xml))


(defn save-sitemap
  [f sitemap-xml]
  "Save the sitemap XML to a UTF-8 encoded File."
  (spit-utf8 f sitemap-xml))


(defn validate-sitemap
  "Validate a  that contains an XML sitemap
   against the sitemaps.org schema and return a list of validation errors.
   If the Sitemap is valid then the list will be empty. If the XML is
   structurally invalid then throws SAXParseException."
  [{:keys [:siteindex :sitemaps] :as sitemap-record}]
  (let [errors (transient [])]
    (when siteindex
      (let [in (jio/file siteindex)
            in (if (gzipped? in) (GZIPInputStream. in) in)]
        (reduce conj! errors (v/validate-siteindex in))))
    (when-not (empty? sitemaps)
      (run!
        (fn [sitemap]
          (let [in (jio/file sitemap)
                in (if (gzipped? in) (GZIPInputStream. in) in)]
            (reduce conj! errors (v/validate-sitemap in))))
        sitemaps))
    (persistent! errors)))
