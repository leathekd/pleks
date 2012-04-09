(ns pleks.test.client
  (:use [clojure.test]
        [pleks.client])
  (:require [clj-http.client :as http]))

(def url "http://url1.com/foo")
(def lib-url "http://url1.com/library")
(def root (with-meta {:my-plex "1"
                      :size "14"
                      :webkit "1"
                      :platform "MacOSX"
                      :children [(with-meta {:count "1"
                                             :key "accounts"
                                             :title "accounts"}
                                   {:plex-url (str url "/accounts")})
                                 (with-meta {:count "1"
                                             :key "/library"
                                             :title "library"}
                                   {:plex-url "http://url1.com/library"})]}
            {:plex-url url}))

(deftest test-hyphenize
  (are [b a] (= a (hyphenize b))
       "This_IS_tricky" "this-is-tricky"
       "someID" "some-id"
       "other_id" "other-id"))

(deftest test-normalize-keys
  (is (= {:this-is-tricky 1 :some-id 2 :other-id 3}
         (normalize-keys {:This_IS_tricky 1 :someID 2 :other_id 3}))))

(deftest test-body-xml
  (is (= {:tag :test :attrs nil
          :content [{:tag :child :attrs nil :content ["1"]}]}
         (body-xml {:body "<test><child>1</child></test>"}))))

(deftest test-base-url
  (are [b a] (= a (base-url b))
       "https://www.duckduckgo.com/?q=plex" "https://www.duckduckgo.com"
       "https://www.duckduckgo.com:80/?q=plex" "https://www.duckduckgo.com:80"))

(deftest test-visit
  (let [resp {:tag :MediaContainer,
              :attrs {:myPlex "1"
                      :size "14"
                      :webkit "1"
                      :platform "MacOSX"}
              :content [{:tag :Directory
                         :attrs {:count "1", :key "accounts", :title "accounts"}
                         :content nil}
                        {:tag :Directory
                         :attrs {:count "1", :key "/library", :title "library"}
                         :content nil}]}
        urls (atom [])]
    (with-redefs [body-xml identity
                  http/get (fn [url & _]
                             (swap! urls conj url)
                             resp)]
      (let [visited (visit url)]
        (is (= visited root))
        (is (= (:plex-url (meta visited)) url))
        (is (= (str url "/accounts")
               (:plex-url (meta (first (:children visited))))))
        (is (= (str lib-url)
               (:plex-url (meta (second (:children visited))))))
        (visit (second (:children visited)))
        (is (= (set @urls) #{url lib-url}))))))

(deftest test-node-matches
  (let [node {:count "1", :key "/library", :title "library"}]
    (is (node-matches? #"/lib" nil node))
    (is (node-matches? #"^lib" :title node))))

(deftest test-find-child
  (is (= {:count "1", :key "/library", :title "library"}
         (find-child root #"library" :title))))

(deftest test-visit-child
  (let [urls (atom [])]
    (with-redefs [body-xml identity
                  http/get (fn [url & _]
                             (swap! urls conj url)
                             {:foo "bar" :children []})]
      (visit-child root #"/library")
      (is @urls [lib-url]))))

(deftest test-visit-in
  (let [fake-node {:children [{:key "library"}
                              {:key "sections"}
                              {:title "TV Shows"}
                              {:title "Firefly"}]}
        args (atom [])
        to-visit [#"library" #"sections" {:title #"^TV"} {:title #"Firefly"}]]
    (with-redefs [visit-child (fn [node re & [key]]
                                (swap! args conj (if key
                                                   {key re}
                                                   re))
                                fake-node)]
      (visit-in fake-node to-visit)
      (is (= (set to-visit) (set @args))))))
