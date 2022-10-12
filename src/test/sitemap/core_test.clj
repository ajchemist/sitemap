(ns sitemap.core-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.java.io :as jio]
   [clojure.xml :as xml]
   [sitemap.core :refer :all]
   )
  (:import
   java.io.File
   java.util.zip.GZIPInputStream
   ))


(def sample-sitemap-xml
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"><url><loc>http://hashobject.com/about</loc><lastmod>2013-05-31</lastmod><changefreq>monthly</changefreq><priority>0.8</priority></url><url><loc>http://hashobject.com/team</loc><lastmod>2013-06-01</lastmod><changefreq>monthly</changefreq><priority>0.9</priority></url></urlset>")


(def sample-sitemap-xml-without-optional-fields
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"><url><loc>http://hashobject.com/about</loc></url><url><loc>http://hashobject.com/team</loc><changefreq>yearly</changefreq></url></urlset>")


(deftest correct-sitemap-redner-test
  (testing "Sitemap was rendered incorrectly."
    (is
      (=
        sample-sitemap-xml
        (render-sitemap
          [{:loc        "http://hashobject.com/about"
            :lastmod    "2013-05-31"
            :changefreq "monthly"
            :priority   "0.8"}
           {:loc        "http://hashobject.com/team"
            :lastmod    "2013-06-01"
            :changefreq "monthly"
            :priority   "0.9"}])))))


(deftest entry-without-optional-fields-test
  (testing "Sitemap was rendered incorrectly because of errors with optional fields."
    (is
      (=
        sample-sitemap-xml-without-optional-fields
        (render-sitemap [{:loc "http://hashobject.com/about"}
                         {:loc        "http://hashobject.com/team"
                          :changefreq "yearly"}])))))


(deftest encoding-test
  (testing "We can round-trip non-ascii characters."
    (let [tmp (File/createTempFile "sitemap-" ".xml")]
      (->>
          (render-sitemap [{:loc "http://example.com/Iñtërnâtiônàlizætiøn/"}])
        (save-sitemap tmp))
      (is (= "http://example.com/Iñtërnâtiônàlizætiøn/"
            (-> (xml/parse tmp)
              (get :content)
              (first)
              (get :content)
              (first)
              (get :content)
              (first)))))))


(deftest chunk-test
  (testing ""
    (is
      (empty?
        (-> (render-sitemap-and-save*
              "http://example.com"
              "target/chunk-test/sitemap"
              (map
                (fn [i] {:loc (str "http://example.com/" i)})
                (range 0 1e5)))
          (validate-sitemap))))))
