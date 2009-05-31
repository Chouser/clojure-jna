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

(def #^{:private true} flag-bits
  {:access          0x00000001  ; File was accessed
   :modify          0x00000002  ; File was modified
   :attrib          0x00000004  ; Metadata changed
   :close-write     0x00000008  ; Writtable file was closed
   :close-nowrite   0x00000010  ; Unwrittable file closed
   :close           0x00000018
   :open            0x00000020  ; File was opened
   :moved-from      0x00000040  ; File was moved from X
   :moved-to        0x00000080  ; File was moved to Y
   :moved           0x000000C0
   :create          0x00000100  ; Subfile was created
   :delete          0x00000200  ; Subfile was deleted
   :delete-self     0x00000400  ; Self was deleted
   :move-self       0x00000800  ; Self was moved
   :unmount         0x00002000  ; Backing fs was unmounted
   :q-overflow      0x00004000  ; Event queued overflowed
   :ignored         0x00008000  ; File was ignored
   :onlydir         0x01000000  ; only watch the path if it is a directory
   :dont-follow     0x02000000  ; don't follow a sym link
   :mask-add        0x20000000  ; add to the mask of an already existing watch
   :isdir           0x40000000  ; event occurred against dir
   :oneshot         0x80000000  ; only send event once
   :all-events      0x00000FFF})

(defn flags-mask-fn
  "Returns a bit-mask number for the flags collection given.  Valid
  flags are keyword forms of the flags listed in the inotify(7) man
  page.  For example, use :access for IN_ACCESS.  Note you probably
  don't need to call this function directly: add-iwatch can take the
  same collection of flags, or you can use the flags-mask macro."
  [flags]
  (reduce bit-or (map #(or (flag-bits %)
                           (throw (Exception. (str "Invalid flag " %))))
                      flags)))

(defmacro flags-mask
  "Returns a bit-mask number for the flags given. Valid flags are
  symbol forms of the flags listed in the inotify(7) man page.  For
  example, use (flags-mask access) for IN_ACCESS.  Throws an exception
  at compile time if invalid flags are given."
  [& flags]
  (flags-mask-fn (map #(keyword (str %)) flags)))

(defn- mask-flags
  "Returns a set of flag keywords for the event bit-mask"
  [mask]
  (set (map key (filter #(zero? (bit-and-not (val %) mask)) flag-bits))))

(defn- read-events
  "Agent action that blocks on the inotify fd and a control pipe.
  inotify events are read and dispatched in this thread.  The control
  pipe can cause the agent to close the fd's and stop looping, or just
  to self-send once to load any new definition of read-events."
  [_ inoti]
  (let [[readfds] (select #{(:readfd inoti) (:ifd inoti)})] ; blocks
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
              (reset! (:wdmap inoti) :done)
              :closing)
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
              (when (pos? name-len)
                (.get event-buf name-bytes))
              ; There's a race here when a watch is added to the fd
              ; before it's in wdmap.  However, if the event had come in
              ; a moment earlier it would have been lost too, so we'll
              ; just ignore events whose wd is not in wdmap.
              (when-let [handler (get @(:wdmap inoti) wd)]
                (let [event {:wd wd, :mask mask, :flags (mask-flags mask)
                             :cookie cookie}]
                  (if (pos? name-len)
                    (let [name-str (String. name-bytes)]
                      (assoc event :name (.substring
                                           name-str 0 (.indexOf name-str 0))))
                    event))))))

        (send-off *agent* read-events inoti)
        :normal))))


(defn iinit
  "Creates a new inotify instance and returns an object representing
  it.  Use add-iwatch to add files or directories to the watch list."
  []
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
  "Adds pathname to the watchlist of the given inotify object.  Use
  the flags-mask macro to build flags, or pass in a collection of
  keywords (see flags-mask-fn for details).  The handler function will
  be called when the given event occurs, with a single arg: a hash-map
  with keys like :flags (a set of keywords indicating the kind of
  event that occured), :name (for the name of the file updated when
  watching a directory), :cookie, etc.  See inotify(7) man page for
  more details on each of these fields.  The function will be called
  in the inotify reader thread, so it should complete promptly or
  coordinate with another thread to complete its work.  Returns
  a watch descriptor that can safely be ignored unless you want to
  explicitly pass it to rm-iwatch later."
  [inoti pathname flags handler]
  (let [wd (when-err (libc/inotify_add_watch
                       (:ifd inoti)
                       pathname
                       (if (number? flags) flags (flags-mask-fn flags)))
             "Error adding inotify watch")]
    (swap! (:wdmap inoti) assoc wd handler)
    wd))

(defn rm-iwatch
  "Removes an item from the given inotify object's watchlist, as
  specified by the given watch descriptor."
  [inoti wd]
  (let [wd (when-err (libc/inotify_rm_watch (:ifd inoti) wd)
             "Error adding inotify watch")]
    (swap! (:wdmap inoti) dissoc wd)
    inoti))

(defn iclose
  "Closes down an inotify object.  Blocks until the file descriptors
  are all closed and the agent has stopped looping."
  [inoti]
  (libc/write (:writefd inoti) (pointer (make-cbuf 1)) 1)
  (while (not= @(:agent inoti) :done)
    (send-off (:agent inoti) #(if (= % :closing) :done %))
    (await (:agent inoti)))
  inoti)

(defn- reload-read-events [inoti]
  (let [buf (make-cbuf 1)]
    (.put buf (byte 1))
    (libc/write (:writefd inoti) (pointer buf) 1))
  nil)


(comment
; Example usage:

(def x (iinit))

(add-iwatch x "/tmp" (flags-mask create delete)
            #(if (:create (:flags %))
               (println "Created file" (:name %) "in /tmp")
               (println "Deleted file" (:name %) "in /tmp")))

(def wd (add-iwatch x "/tmp" (flags-mask moved) prn))
(rm-iwatch x wd)

(iclose x)
)

