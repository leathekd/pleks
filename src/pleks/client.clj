(ns pleks.client
  (:use [clojure.java.io :only [input-stream]])
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [clojure.xml :as xml])
  (:import (java.net URL)))

(defn hyphenize
  "Transform spaces, underscores, and capitalization into hyphens"
  [s]
  (-> s
      (str/replace #"_|\s+" "-")
      (str/replace #"^([A-Z]{2,})" #(str (.toLowerCase (nth % 1)) "-"))
      (str/replace #"(.)([A-Z]+)" #(str (nth % 1) "-" (nth % 2)))
      (.toLowerCase)
      (str/replace #"-+" "-")))

(defn normalize-keys
  "Reformat keys in a more Clojure-like manner. Source adjusted from
  clojure.walk/keyify-keys."
  [m]
  (let [f (fn [[k v]] [(-> k name hyphenize keyword) v])]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn body-xml
  "Extract and parse the body of a get response."
  [response]
  (when (:body response)
    (xml/parse (input-stream (.getBytes (:body response) "UTF-8")))))

(defn base-url
  "Given a url string, extract the protocol, server, and port."
  [url]
  (let [u (URL. url)]
    (format "%s://%s%s"
            (.getProtocol u)
            (.getHost u)
            (let [port (.getPort u)]
              (if (pos? port)
                (str ":" port)
                "")))))

(defmulti visit
  "Fetch the given URL or node. The child nodes fetched by visit will
  have metadata attached to them to indicate what URL to fetch."
  class)

(defmethod visit String
  [url]
  (let [resp (body-xml (http/get url))]
    (with-meta
      (assoc (normalize-keys (:attrs resp))
        :children
        (for [node (:content resp)
              :let [n (normalize-keys (:attrs node))
                    k (:key n)]]
          (if k
            (with-meta n
              {:plex-url (if (.startsWith k "/")
                       (str (base-url url) k)
                       (str url "/" k))})
            n)))
      {:plex-url url})))

(defmethod visit java.util.Map
  [node]
  (visit (:plex-url (meta node))))

(defn node-matches?
  "Will return true if the value of key matches the given regex. If no
  key is passed, :key will be used."
  [re key node]
  (boolean (re-find re (get node (or key :key)))))

(defn find-child
  "Search the child nodes of node for the first that matches the given
  regex and optional key. If no key is passed, :key will be used."
  [node re & [key]]
  (first (filter (partial node-matches? re key) (:children node))))

(defn visit-child
  "Visit the first child of node that matches the given regex and
  optional key. If no key is passed, :key will be used."
  [node re & [key]]
  (visit (find-child node re key)))

(defn visit-in
  "Takes the result of a call to 'visit' as the root node and a seq of
  either regular expressions or maps (or a combination). In the map,
  the given key is the key to search in the child node and the value
  is the regular expression to match.

  For example: (visit-in (visit \"plexurl\")
                                [#\"library\" #\"sections\"
                                 #\"^TV\" {:title #\"Firefly\"}
                                 {:title #\"Season 1\"}
                                 {:title #\"Jaynestown\"}])"
  [root child-matchers]
  (reduce (fn [node matcher]
            (if (map? matcher)
              (visit-child node (first (vals matcher)) (first (keys matcher)))
              (visit-child node matcher)))
          root
          child-matchers))
