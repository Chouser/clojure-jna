; Copyright (c) Chris Houser, May 2009. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns
  #^{:author "Chris Houser"
     :doc "Convenient clojure functions providing linux-specific
           features via JNA"}
  net.n01se.clojure-jna.linux
  (:use [net.n01se.clojure-jna :only [jna-ns make-cbuf pointer when-err]]
        [net.n01se.clojure-jna.libc-utils :only [select]]))

(jna-ns libc c [Integer inotify_init,
                Integer inotify_add_watch,
                Integer inotify_rm_watch,
                Integer pipe,
                Integer ioctl,
                Integer read,
                Integer write,
                Integer close])

(def #^{:private true} FIONREAD 0x541B)

(defn- read-events [_ inoti]
  (let [[readfds] (select #{(:readfd inoti) (:ifd inoti)})]
    (if (readfds (:readfd inoti))

      ; IPC from some controlling thread
      (let [buf (make-cbuf 1)]
        (when-err (libc/read (:readfd inoti) (pointer buf) 1)
          "Error reading from pipe")
        (condp = (int (.get buf))
          0 (do
              (when-err (libc/close (:readfd inoti))
                "Error closing pipe (read)")
              (when-err (libc/close (:writefd inoti))
                "Error closing pipe (write)")
              (reset! (:wdmap inoti) :closed)
              :closed)
          1 (do
              (send-off *agent* read-events inoti)
              :forced-reload)))

      ; inotify event
      (let [byte-count-buf (make-cbuf 4)]
        (when-err (libc/ioctl (:ifd inoti) FIONREAD (pointer byte-count-buf))
          "Error getting byte-count via ioctl")

        (let [byte-count (.getInt byte-count-buf)
              event-buf (make-cbuf byte-count)]
          (when-err (libc/read (:ifd inoti) (pointer event-buf) byte-count)
            "Error reading events")
          (while (.hasRemaining event-buf)
            (let [wd       (.getInt event-buf)
                  mask     (.getInt event-buf)
                  cookie   (.getInt event-buf)
                  name-len (.getInt event-buf)
                  name-bytes (make-array Byte/TYPE name-len)]
              (.get event-buf name-bytes)
              ; There's a race here when a watch is added to the fd
              ; before it's in wdmap.  However, if the event had come in
              ; a moment earlier it would have been lost too, so we'll
              ; just ignore events whose wd is not in wdmap.
              (when-let [handler (get @(:wdmap inoti) wd)]
                (let [name-str (String. name-bytes)
                      name-str (.substring name-str 0 (.indexOf name-str 0))]
                  (handler {:wd wd, :mask mask,
                            :cookie cookie, :name name-str}))))))

        (send-off *agent* read-events inoti)
        :normal))))


(defn iinit []
  (let [buf (make-cbuf 16)
        _ (when-err (libc/pipe (pointer buf)) "Error creating pipe")
        inoti {:agent (agent :init)
               :readfd (.getInt buf)
               :writefd (.getInt buf)
               :ifd (libc/inotify_init)
               :wdmap (atom {})}]
    (send-off (:agent inoti) read-events inoti)
    inoti))

(defn add-iwatch
  "The interface for this is going to change.  Don't use it yet."
  [inoti pathname mask handler]
  (let [wd (when-err (libc/inotify_add_watch (:ifd inoti) pathname mask)
             "Error adding inotify watch")]
    (swap! (:wdmap inoti) assoc wd handler)))

(defn iclose [inoti]
  (libc/write (:writefd inoti) (pointer (make-cbuf 1)) 1))

(defn- reload-read-events [inoti]
  (let [buf (make-cbuf 1)]
    (.put buf (byte 1))
    (libc/write (:writefd inoti) (pointer buf) 1)))

(def x (iinit))
(add-iwatch x "/tmp" 0x100 prn)
