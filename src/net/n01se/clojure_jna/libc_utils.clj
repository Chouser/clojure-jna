; Copyright (c) Chris Houser, May 2009. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns
  #^{:author "Chris Houser"
     :doc "Dumping-ground for libc's 'select' function, until I find
           a better place to put it, or other things to put here"}
  net.n01se.clojure-jna.libc-utils
  (:use [net.n01se.clojure-jna :only [jna-ns make-cbuf pointer when-err]]))

(jna-ns libc c [Integer select])

(defn select
  "Block for timeout-secs waiting for events on the given file
   descriptors.  Each of readfds, writefds, and exceptfds must be
   a collection of zero or more file descriptors (that is, integers),
   or nil.  Returns a vector of three sets indicating the file
   descriptors that have waiting events."
  [readfds & [writefds exceptfds timeout-secs]]
  (let [FD_SETSIZE 1024
        bytes-per-int 4
        bits-per-byte 8
        FD_NFDBITS (* bytes-per-int bits-per-byte)
        howmany (quot (+ FD_SETSIZE FD_NFDBITS -1) FD_NFDBITS)
        fd-bytes (* howmany bytes-per-int)

        set-to-buf (fn [fds]
                     (when (seq fds)
                       (let [set-buf (.asIntBuffer (make-cbuf fd-bytes))]
                         (doseq [fd fds]
                           (let [index (quot fd FD_NFDBITS)
                                 offset (rem fd FD_NFDBITS)]
                             (.put set-buf index
                                   (bit-or (.get set-buf index)
                                           (bit-shift-left 1 offset)))))
                         set-buf)))

        buf-to-set (fn [fdset fdbuf]
                     (set (remove
                            #(let [index (quot % FD_NFDBITS)
                                   offset (rem % FD_NFDBITS)]
                               (zero? (bit-and (.get fdbuf index)
                                               (bit-shift-left 1 offset))))
                            fdset)))

        readfds-buf   (set-to-buf readfds)
        writefds-buf  (set-to-buf writefds)
        exceptfds-buf (set-to-buf exceptfds)

        timeval (when timeout-secs
                  (pointer
                    (-> (make-cbuf 16)
                        (.putLong (long timeout-secs))
                        (.putLong (long (* (rem timeout-secs 1) 1000000))))))]
    (when-err (libc/select
                (inc (apply max (concat readfds writefds exceptfds)))
                (pointer readfds-buf)
                (pointer writefds-buf)
                (pointer exceptfds-buf)
                timeval)
      "Error in select")

    [(buf-to-set readfds   readfds-buf)
     (buf-to-set writefds  writefds-buf)
     (buf-to-set exceptfds exceptfds-buf)]))

