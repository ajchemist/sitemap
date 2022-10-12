(ns sitemap.validator
  (:require
   [clojure.java.io :as jio]
   )
  (:import
   java.net.URL
   java.io.File
   java.io.InputStream
   java.io.StringReader
   javax.xml.XMLConstants
   javax.xml.parsers.DocumentBuilder
   javax.xml.parsers.DocumentBuilderFactory
   javax.xml.validation.SchemaFactory
   org.xml.sax.ErrorHandler
   org.xml.sax.InputSource
   ))


(set! *warn-on-reflection* true)


; The latest version of the sitemaps.org schema.
(def ^URL sitemap-xsd (jio/resource "org/sitemaps/schemas/0.9/sitemap.xsd"))
(def ^URL siteindex-xsd (jio/resource "org/sitemaps/schemas/0.9/siteindex.xsd"))


(defn- read-schema
  "Read the XSD identified by the URL from the classpath
   into a Schema object."
  [^URL xsd-url]
  (-> (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)
    (.newSchema xsd-url)))


(defn new-document-builder
  "Make a DocumentBuilder that checks namespaces
   and validates against a Schema."
  ^DocumentBuilder
  [^URL xsd-url]
  (->
    (doto
      (DocumentBuilderFactory/newInstance)
      (.setNamespaceAware true)
      (.setSchema (read-schema xsd-url)))
    (.newDocumentBuilder)))


; http://docs.oracle.com/javase/7/docs/api/org/xml/sax/ErrorHandler.html
(defn new-throwing-error-handler
  "Create an error handler that appends Exceptions to a list
   wrapped in an atom."
  [error-list]
  (reify
    ErrorHandler
    (warning    [_ e] (swap! error-list conj e))   ; (throw e))
    (error      [_ e] (swap! error-list conj e))   ; (throw e))
    (fatalError [_ e] (swap! error-list conj e)))) ; (throw e))))


(defmulti  parse-xml-document (fn [in _db] (class in)))


(defmethod parse-xml-document File [^File in ^DocumentBuilder db]
  (.parse db in))


(defmethod parse-xml-document InputStream [^InputStream in ^DocumentBuilder db]
  (.parse db in))


(defmethod parse-xml-document String [in ^DocumentBuilder db]
  (with-open [r (StringReader. in)]
    (.parse db (InputSource. r))))


(defn- validate-xml
  "Validate a File, String or InputStream containing an XML sitemap."
  [xsd-url in]
  (let [errors (atom [])]
    (->> (doto (new-document-builder xsd-url)
           (.setErrorHandler (new-throwing-error-handler errors)))
      (parse-xml-document in))
    @errors))


(defn validate-sitemap
  [in]
  (validate-xml sitemap-xsd in))


(defn validate-siteindex
  [in]
  (validate-xml siteindex-xsd in))


(set! *warn-on-reflection* false)
