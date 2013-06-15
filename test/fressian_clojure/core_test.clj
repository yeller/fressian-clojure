(ns fressian-clojure.core-test
  (:require [clojure.test :refer :all]
            [org.fressian.clojure :as fr]))

(defn recode [obj]
  (fr/decode (fr/encode obj)))

(deftest smoke-test
  (testing "I can encode clojure maps"
    (let [obj {:test 'test}]
      (is (= obj (recode obj))))))
