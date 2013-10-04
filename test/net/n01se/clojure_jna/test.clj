(ns net.n01se.clojure-jna.test
  (:require [net.n01se.clojure-jna :as jna])
  (:use [clojure.test :only [deftest is]]))

(deftest test-jna-invoke
  (is (= 13 (jna/jna-invoke Integer c/printf "My number: %d\n" 5))))

(deftest test-jna-fn
  (let [c-printf (jna/jna-fn Integer c/printf)]
    (is (= 13 (c-printf "My number: %d\n" 5)))))

(deftest test-jna-ns
  (jna/jna-ns native-c c [Integer printf, Integer open, Integer close])
  (is (= 0 (eval '(native-c/close 0))))
  (is (= 14 (eval '(native-c/printf "one %s two\n" "hello")))))
