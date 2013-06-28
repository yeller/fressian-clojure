# fressian-clojure

A simple wrapper for the fressian binary encoding protocol that makes it
easy to use from and for clojure.  No attention paid to performance yet.

Support clojure keywords, symbols, and maps in addition to the default
fressian options.

## Usage

    :dependencies [fressian-clojure "0.2.0"]

    (:use [org.fressian.clojure :only [decode encode add-handler]])

### Very simple to use 

    (def byte-array (fr/encode obj) ;; returns byte[]

    (fr/decode byte-array)          ;; returns obj

### Can add your own global handlers for new types:

    (fr/add-handler org.type.MyType "mytag" <writer> <reader>)

This is a critical facility if you wish to retain the types of
defrecords or deftype values during decoding.  For defrecords, the
decoded result is a plain map.  See the file clojure.clj for examples
of writers and readers and other methods of interest.  

entity.clj has an example of custom encoding/decoding methods for
datomic.

### Caveats

- Should be reasonably efficient, but no guarantees about encoding/decoding optimality, especially for collection types
- Default serializer handlers do not distinguish between array and hash maps
- Records are deserialized as maps by default (see above)
- Structs are always deserialized as maps
- Collection metadata is ignored
- Functions cannot be serialized
- No support for persistent queues yet

## License

Copyright (c) Metadata Partners, 2013. All rights reserved.

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0
(http://opensource.org/licenses/eclipse-1.0.php).  By using this
software in any fashion, you are agreeing to be bound by the terms of
this license.  You must not remove this notice, or any other, from
this software.
