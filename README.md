**Dynamically load and use native C libs from Clojure using JNA**

clojure-jna is available from clojars. Just add this to your Leiningen `project.clj` in the `:deps` section:

```clojure
[n01se.net/clojure-jna "1.0.0"]
```
===
Usage
===

```clojure
(require '[net.n01se.clojure-jna :as jna])
(jna/invoke Integer c/printf "My number: %d\n" 5)
; My number: 5
;=> 13
```

The first argument to jna-invoke is the native function's return value.  The
second is a symbol, in this case c/printf.  The `c` part is the name of the
library, in this case `libc`.  The `printf` part is of course the name of the
function to call.  The rest are arguments to the native function.

That 13 is `printf`s return value -- I guess it's the number of bytes printed or
something?  Anyway, feel free to ignore it just like all C programs do.

If you're going to be calling the same function a few times, you might find it
convenient to be able to call it like a regular Clojure function.  Use jna-fn
for that:

```clojure
(doc jna/to-fn)
; -------------------------
; net.n01se.clojure-jna/to-fn
; ([return-type function-symbol])
; Macro
;   Return a Clojure function that wraps a native library function:
;    (def c-printf (jna/to-fn Integer c/printf))
;    (c-printf "My number: %d\n" 5)

(def c-printf (jna/to-fn Integer c/printf))

(c-printf "My number: %d\n" 5)
; My number: 5
;=> 13

(c-printf "My number: %d\n" 10)
; My number: 10
;=> 14
```

If you're going to be calling a bunch of functions from the same native lib, you
might like to use jna-ns to create a Clojure namespace full of Clojure functions
that wrap the native functions:

```clojure
(doc jna/to-ns)
; -------------------------
; net.n01se.clojure-jna/to-ns
; ([new-ns libname fnspecs])
; Macro
;   Create a namespace full of Clojure functions that wrap functions from
;   a native library:
;   (jna/to-ns native-c c [Integer printf, Integer open, Integer close])
;   (native-c/printf "one %s two\n" "hello")

(jna/to-ns native-c c [Integer printf, Integer open, Integer close])
;=> #<Namespace native-c>

(native-c/printf "one %s two\n" "hello")
; one hello two
;=> 14

(native-c/open "README")
;=> -1
```
