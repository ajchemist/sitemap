(ns sitemap.validator
  (:require
   [clojure.java.io :as jio]
   )
  (:import
   java.io.File
   java.io.InputStream
   java.io.StringReader
   javax.xml.XMLConstants
   javax.xml.parsers.DocumentBuilderFactory
   javax.xml.transform.stream.StreamSource
   javax.xml.validation.SchemaFactory
   org.xml.sax.ErrorHandler
   org.xml.sax.InputSource
   ))


; The latest version of the sitemaps.org schema.
(def sitemap-xsd (jio/resource "org/sitemaps/schemas/0.9/sitemap.xsd"))
(def siteindex-xsd (jio/resource "org/sitemaps/schemas/0.9/siteindex.xsd"))


(defn- read-schema
  [xsd-url]
  "Read the XSD identified by the URL from the classpath
   into a Schema object."
  (-> (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)
    (.newSchema xsd-url)))


(defn new-document-builder
  [xsd-url]
  "Make a DocumentBuilder that checks namespaces
   and validates against a Schema."
  (->
    (doto
      (DocumentBuilderFactory/newInstance)
      (.setNamespaceAware true)
      (.setSchema (read-schema xsd-url)))
    (.newDocumentBuilder)))


; http://docs.oracle.com/javase/7/docs/api/org/xml/sax/ErrorHandler.html
(defn new-throwing-error-handler
  [error-list]
  "Create an error handler that appends Exceptions to a list
   wrapped in an atom."
  (reify
    ErrorHandler
    (warning    [this e] (swap! error-list conj e)); (throw e))
    (error      [this e] (swap! error-list conj e)); (throw e))
    (fatalError [this e] (swap! error-list conj e)))); (throw e))))


(defmulti  parse-xml-document (fn [in db] (class in)))


(defmethod parse-xml-document File [in db]
  (.parse db in))


(defmethod parse-xml-document InputStream [in db]
  (.parse db in))


(defmethod parse-xml-document String [in db]
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
