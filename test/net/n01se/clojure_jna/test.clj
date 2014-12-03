(ns net.n01se.clojure-jna.test
  (:require [net.n01se.clojure-jna :as jna]
            [net.n01se.clojure-jna.libc-utils :as libc-utils :refer [fopen fclose fileno]]
            [clojure.test :refer [deftest is]]))

(deftest test-jna-invoke
  (is (= 13 (jna/invoke Integer c/printf "My number: %d\n" 5))))

(deftest test-jna-fn
  (let [c-printf (jna/to-fn Integer c/printf)]
    (is (= 13 (c-printf "My number: %d\n" 5)))))

(deftest test-jna-ns
  (jna/to-ns native-c c [Integer printf, Integer open, Integer close])
  (is (= 0 (eval '(native-c/close 0))))
  (is (= 14 (eval '(native-c/printf "one %s two\n" "hello")))))

(deftest test-select
  (let [fp (fopen "project.clj" "r")
        fd (fileno (.getPointer fp))]
    ; There should be no exceptional events on project.clj... I hope...
    (is (= [#{} #{} #{}] (libc-utils/select nil nil [fd] 1)) "select timedout with no events")
    (is (= [#{fd} #{} #{}] (libc-utils/select [fd] nil nil 1)) "select indicated that the fd can be read")    
    (fclose fp)))
