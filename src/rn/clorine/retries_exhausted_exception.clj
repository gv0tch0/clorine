(ns rn.clorine.retries-exhausted-exception
  (:require [clojure.contrib.str-utils :as str-utils])
  (:gen-class
   :name rn.clorine.RetriesExhaustedException
   :extends Exception
   :prefix "retries-exhausted-ex-"
   :constructors {[String java.util.List] [String]
                  [String Throwable java.util.List] [String Throwable]}
   :init init
   :state errors))


(defn retries-exhausted-ex-init
  ([#^String msg #^java.util.List exceptions]
     [[msg] exceptions])
  ([#^String msg #^Throwable ex #^java.util.List exceptions ]
     [[msg ex] exceptions]))


(comment

(def *chicken* (atom nil))

 (let [errors [(RuntimeException. "blah") (RuntimeException. "lunch ")]]
   (reset! *chicken*
           (rn.clorine.RetriesExhaustedException.
            (retries-exhausted-get-errors errors)
            errors)))

(.getMessage @*chicken*)





 (compile 'rn.clorine.retries-exhausted-exception)

)