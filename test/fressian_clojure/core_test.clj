(ns fressian-clojure.core-test
  (:require [clojure.test :refer :all]
            [org.fressian.clojure :as fr]))

(defn recode [obj]
  (fr/decode (fr/encode obj)))

(defn equivalent? [obj]
  (let [re (recode obj)]
    (and (= obj re)
         (= (hash obj) (hash obj))
         (= (type obj) (type re)))))

(deftest numbers
  (testing "Numerics"
    (is (equivalent? 1))
    (is (equivalent? -1))
    (is (equivalent? 0))
;;    (is (equivalent? (int 1)))
    (is (equivalent? (float 1)))
    (is (equivalent? (double 1)))
    (is (equivalent? 12311212512512412412312N))
    (is (equivalent? 12311212512512.412412312M))
    (is (equivalent? 122/21))))

(deftest characters
  (testing "Character types"
    (is (equivalent? \f))
    (is (equivalent? "foo"))
    (is (equivalent? 'foo))
    (is (equivalent? :foo))
    (is (equivalent? (java.util.UUID/randomUUID)))))

(defstruct teststruct :test1 :test2)

(deftest aggregates
  (testing "Aggregate types"
    (is (equivalent? #{1 2 3}))
    (is (equivalent? '(1 2 3)))
    (is (equivalent? [1 2 3]))
    (is (equivalent? {:a 1 :b 2}))
    (is (equivalent? (sorted-map :b 2 :c 3 :a 1)))
    (is (equivalent? (sorted-set :b :c :a)))
    (is (equivalent? (sorted-set 54 612 2.3 15125M)))
    (is (equivalent? [#{1 2 3} [1 2 3] '(1 2 3) \f "foo" :foo]))
    (let [smap (struct-map teststruct :test1 10 :test2 20 :test3 30)]
      (= (recode smap) smap))))
    
