; Copyright (c) Chris Houser, May 2009. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns
  #^{:author "Chris Houser"
     :doc "Dynamically load and use native C libs from Clojure using JNA"}
  net.n01se.clojure-jna
  (:import (com.sun.jna Native)))

(defn- get-function [s]
  `(com.sun.jna.Function/getFunction ~(namespace s) ~(name s)))

(defmacro jna-invoke
  "Call a native library function:
  (jna-invoke Integer c/printf \"My number: %d\\n\" 5)"
  [return-type function-symbol & args]
  `(.invoke ~(get-function function-symbol) ~return-type (to-array [~@args])))

(defmacro jna-fn
  "Return a Clojure function that wraps a native library function:
   (def c-printf (jna-fn Integer c/printf))
   (c-printf \"My number: %d\\n\" 5)"
  [return-type function-symbol]
  `(let [func# ~(get-function function-symbol)]
     (fn [& args#]
       (.invoke func# ~return-type (to-array args#)))))

(defmacro jna-ns
  "Create a namespace full of Clojure functions that wrap functions from
  a native library:
  (jna-ns native-c c [Integer printf, Integer open, Integer close])
  (native-c/printf \"one %s two\\n\" \"hello\")"
  [new-ns libname fnspecs]
  `(do
     (create-ns '~new-ns)
     ~@(for [[return-type fn-name] (partition 2 fnspecs)]
         `(intern '~new-ns '~fn-name
                  (jna-fn ~return-type ~(symbol (str libname) (str fn-name)))))
     (the-ns '~new-ns)))

(defn make-cbuf
  "Create a direct ByteBuffer of the given size with little-endian
   byte order.  This is useful for creating structs to pass to
   native functions.  See also 'pointer'"
  [size]
  (-> (java.nio.ByteBuffer/allocateDirect size)
      (.order java.nio.ByteOrder/LITTLE_ENDIAN)))

(defn pointer
  "Pass in a ByteBuffer (such as created by make-cbuf) and this will
   return a JNA Pointer that can be passed directly to JNA-wrapped
   native functions."
  [direct-buffer]
  (when direct-buffer
    (Native/getDirectBufferPointer direct-buffer)))

(defn when-err
  "If value is negative one (-1), throws an excpetion with the given
   msg and the current errno.  Otherwise returns value."
  [value msg]
  (if (== -1 value)
    (throw (Exception. (str msg ", errno: " (Native/getLastError))))
    value))
