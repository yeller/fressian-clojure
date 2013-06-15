# fressian-clojure

A simple wrapper for the fressian binary encoding protocol that makes it
easy to use from and for clojure.  No attention paid to performance yet.

## Usage

:dependencies [fressian-clojure "0.1.0"]

(:use [org.fressian.clojure :only [decode encode add-handler]])

Support clojure keywords, symbols, and maps in addition to the default
fressian options.

(def byte-array (fr/encode obj) ;; returns byte[]
(fr/decode byte-array)          ;; returns obj

Can add global handlers for new types:

(fr/add-handler org.type.MyType "mytag" <writer> <reader>)

See the file clojure.clj for examples of writers and readers and other methods of interest


## License

Copyright (c) Metadata Partners, 2013. All rights reserved.

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0
(http://opensource.org/licenses/eclipse-1.0.php).  By using this
software in any fashion, you are agreeing to be bound by the terms of
this license.  You must not remove this notice, or any other, from
this software.
