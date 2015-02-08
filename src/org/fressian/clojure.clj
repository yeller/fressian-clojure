(ns org.fressian.clojure
  (:refer-clojure :exclude [pr read])
  (:require [clojure.java.io :as io])
  (:import
   [java.io InputStream OutputStream EOFException]
   java.nio.ByteBuffer
   java.nio.charset.Charset
   [org.fressian FressianWriter StreamingWriter FressianReader Writer Reader]
   [org.fressian.handlers WriteHandler ReadHandler ILookup  WriteHandlerLookup]
   [org.fressian.impl ByteBufferInputStream BytesOutputStream]))


;; move into get, a la Clojure lookup?
(defn as-lookup
  "Normalize ILookup or map into an ILookup."
  [o]
  (if (map? o)
    (reify ILookup
           (valAt [_ k] (get o k)))
    o))

(defn write-handler-lookup
  "Returns a fressian write handler lookup that combines fressian's built-in
   handlers with custom-lookup. custom-lookup can be a map or an ILookup,
   keyed by class and returning a single-entry map of tag->write handler.
   Use this to create custom validators, not to create FressianWriters, as
   the latter already call customWriteHandlers internally."
  [custom-lookup]
  (WriteHandlerLookup/createLookupChain (as-lookup custom-lookup)))

(defn ^Writer create-writer
  "Create a fressian writer targetting out. lookup can be an ILookup or
   a nested map of type => tag => WriteHandler."
  ;; TODO: make symmetric with create-reader, using io/output-stream?
  ([out] (create-writer out nil))
  ([out lookup]
     (FressianWriter. out (as-lookup lookup))))

(defn ^Reader create-reader
  "Create a fressian reader targetting in, which must be compatible
   with clojure.java.io/input-stream.  lookup can be an ILookup or
   a map of tag => ReadHandler."
  ([in] (create-reader in nil))
  ([in lookup] (create-reader in lookup true))
  ([in lookup validate-checksum]
     (FressianReader. (io/input-stream in) (as-lookup lookup) validate-checksum)))

(defn fressian
  "Fressian obj to output-stream compatible out.

   Options:
      :handlers    fressian handler lookup
      :footer      true to write footer"
  [out obj & {:keys [handlers footer]}]
  (with-open [os (io/output-stream out)]
    (let [writer (create-writer os handlers)]
      (.writeObject writer obj)
      (when footer
        (.writeFooter writer)))))

(defn defressian
  "Read single fressian object from input-stream-compatible in.

   Options:
      :handlers    fressian handler lookup
      :footer      true to validate footer"
  ([in & {:keys [handlers footer]}]
     (let [fin (create-reader in handlers)
           result (.readObject fin)]
       (when footer (.validateFooter fin))
       result)))

(defn ^ByteBuffer bytestream->buf
  "Return a readable buf over the current internal state of a
   BytesOutputStream."
  [^BytesOutputStream stream]
  (ByteBuffer/wrap (.internalBuffer stream) 0 (.length stream)))

(defn byte-buffer-seq
  "Return a lazy seq over the remaining bytes in the buffer.
   Not fast: intented for REPL usage.
   Works with its own duplicate of the buffer."
  [^ByteBuffer bb]
  (lazy-seq
   (when (.hasRemaining bb)
     (let [next-slice (.slice bb)]
       (cons (.get next-slice) (byte-buffer-seq next-slice))))))

(defn ^ByteBuffer byte-buf
  "Return a byte buffer with the fressianed form of object.
   See fressian for options."
  [obj & {:keys [handlers footer]}]
  (let [baos (BytesOutputStream.)]
    (fressian baos obj :handlers handlers :footer footer)
    (bytestream->buf baos)))

(defn read-batch
  "Read a fressian reader fully (until eof), returning a (possibly empty)
   vector of results."
  [^Reader fin]
  (let [sentinel (Object.)]
    (loop [objects []]
      (let [obj (try (.readObject fin) (catch EOFException e sentinel))]
        (if (= obj sentinel)
          objects
          (recur (conj objects obj)))))))

;;
;; Default handlers
;;

(def clojure-write-handlers
  {clojure.lang.Keyword
   {"key"
    (reify WriteHandler (write [_ w s]
                          (.writeTag w "key" 2)
                          (.writeObject w (namespace s))
                          (.writeObject w (name s))))}

   clojure.lang.Symbol
   {"sym"
    (reify WriteHandler (write [_ w s]
                          (.writeTag w "sym" 2)
                          (.writeObject w (namespace s))
                          (.writeObject w (name s))))}

   java.lang.Character
   {"char"
    (reify WriteHandler (write [_ w s]
                          (.writeTag w "char" 1)
                          (.writeInt w (int s))
                          ))}

   clojure.lang.Ratio
   {"ratio"
    (reify WriteHandler (write [_ w s]
                          (let [^clojure.lang.Ratio r s]
                            (.writeTag w "ratio" 2)
                            (.writeObject w (.numerator r))
                            (.writeObject w (.denominator r)))
                          ))}

   clojure.lang.BigInt
   {"bigint"
    (reify WriteHandler (write [_ w s]
                          (let [^clojure.lang.BigInt i s]
                            (.writeTag w "bigint" 1)
                            (.writeBytes w (.. i toBigInteger toByteArray)))))}

   clojure.lang.PersistentVector
   {"pvec"
    (reify WriteHandler (write [_ w s]
                          (.writeTag w "pvec" 1)
                          (.writeList w s)))}

   clojure.lang.PersistentList
   {"plist"
    (reify WriteHandler (write [_ w s]
                          (.writeTag w "plist" 1)
                          (.writeList w s)))}

   clojure.lang.PersistentTreeMap
   {"spmap"
    (reify WriteHandler (write [_ w s]
                          (.writeTag w "spmap" 1)
                          (let [l (java.util.ArrayList.)]
                            (doseq [[k v] s]
                              (.add l k)
                              (.add l v))
                            (.writeList w l))))}

   clojure.lang.PersistentTreeSet
   {"spset"
    (reify WriteHandler (write [_ w s]
                          (.writeTag w "spset" 1)
                          (.writeList w s)))}


;;   clojure.lang.PersistentList
;;   clojure.lang.PersistentQueue
   })

(def clojure-read-handlers
  {"key"
   (reify ReadHandler (read [_ rdr tag component-count]
                            (keyword (.readObject rdr) (.readObject rdr))))
   "sym"
   (reify ReadHandler (read [_ rdr tag component-count]
                            (symbol (.readObject rdr) (.readObject rdr))))

   "char"
   (reify ReadHandler (read [_ rdr tag component-count]
                            (char (.readInt rdr))))

   "ratio"
   (reify ReadHandler (read [_ rdr tag component-count]
                        (/ (.readObject rdr) (.readObject rdr))))

   "map"
   (reify ReadHandler (read [_ rdr tag component-count]
                        (let [kvs ^java.util.List (.readObject rdr)]
                          (if (< (.size kvs) 16)
                            (clojure.lang.PersistentArrayMap. (.toArray kvs))
                            (clojure.lang.PersistentHashMap/create (seq kvs))))))

   "spmap"
   (reify ReadHandler (read [_ rdr tag component-count]
                        (let [kvs ^java.util.List (.readObject rdr)]
                          (clojure.lang.PersistentTreeMap/create (seq kvs)))))

   "spset"
   (reify ReadHandler (read [_ rdr tag component-count]
                        (let [kvs ^java.util.List (.readObject rdr)]
                          (clojure.lang.PersistentTreeSet/create (seq kvs)))))

   "set"
   (reify ReadHandler (read [_ rdr tag component-count]
                        (let [s ^java.util.HashSet (.readObject rdr)]
                          (set s))))

   "pvec"
   (reify ReadHandler (read [_ rdr tag component-count]
                        (vec (.readObject rdr))))

   "plist"
   (reify ReadHandler (read [_ rdr tag component-count]
                        (apply list (.readObject rdr))))
   })

(extend ByteBuffer
  io/IOFactory
  (assoc io/default-streams-impl
    :make-input-stream (fn [x opts] (io/make-input-stream
                                     (ByteBufferInputStream. x) opts))))

;; Global definitions of fressian handlers
;; - Can override with bindings
;; - Can override with :handlers option to decode/encode

(def ^:dynamic encode-handlers (atom clojure-write-handlers))
(def ^:dynamic decode-handlers (atom clojure-read-handlers))

(defn add-handler [type tag writer reader]
  (swap! encode-handlers assoc type {tag writer})
  (swap! decode-handlers assoc tag reader))

(defn rem-handler [type tag]
  (swap! encode-handlers dissoc type)
  (swap! decode-handlers dissoc tag))

(defn add-handlers
  "Add a sequence of handlers [[type tag writer reader] ...]"
  [list]
  (swap! encode-handlers merge (into {}
                                     (map (fn [[type tag writer _]]
                                            [type {tag writer}])
                                          list)))
  (swap! decode-handlers merge (into {}
                                     (map (fn [[_ tag _ reader]]
                                            [tag reader])
                                          list))))


(defn clear-handlers
  "No special handlers"
  []
  (reset! encode-handlers {})
  (reset! decode-handlers {}))

(defn reset-handlers
  "Reset to the default clojure handler state"
  []
  (reset! encode-handlers clojure-write-handlers)
  (reset! decode-handlers clojure-read-handlers))

;;
;; Simple API for byte[]
;;

(defn faster-merge [m1 m2]
  (if (seq m2)
    (merge m1 m2)
    m1))

(defn encode
  "Encode clojure data in a byte[]"
  [cdata & options]
  (.array (byte-buf cdata
                    :handlers (faster-merge @encode-handlers (:handlers options)))))


(defmulti decode-as-clojure type)

(defmethod decode-as-clojure :default [value]
  value)

(defmethod decode-as-clojure java.math.BigInteger [value]
  (clojure.lang.BigInt/fromBigInteger value))

(defn decode-from
  "Decode a single fressian object from input stream."
  [stream & options]
  (-> (defressian stream :handlers (faster-merge @decode-handlers (:handlers options)))
      decode-as-clojure))

(defn decode
  "Decode a byte array containing fressian clojure data"
  [bdata & options]
  (let [stream (ByteBufferInputStream. (ByteBuffer/wrap bdata))]
    (decode-from stream)))
