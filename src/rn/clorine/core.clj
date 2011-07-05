(ns
    ^{:doc "Clorine: Purified Database Connection Pool Management"
      :authors "Kyle Burton <kyle.burton@gmail.com>, Paul Santa Clara, Josh Crean"}
  rn.clorine.core
  (:require [clojure.contrib.pprint                 :as pp]
            [clojure.contrib.sql                    :as sql]
            [clojure.contrib.str-utils              :as str-utils])
  (:import [org.apache.commons.dbcp  BasicDataSource]
           [rn.clorine RetriesExhaustedException]))

(defonce
  ^{:doc  "Package level connection info registry."
    :added "1.0.0"}
  *connection-registry* (ref {}))

(def
 ^{:doc "Thread local mapping of registered database configuration name to opened connection."
   :added "1.0.0"}
 *curr-thread-connections* nil)

(defn retries-exhausted-get-errors [errors]
  (str-utils/str-join
   "\n"
   (map #(.getMessage %1) errors)))

(defn get-connection [conn-name]
  (if-not (contains? @*connection-registry* conn-name)
    (throw (IllegalArgumentException. (format "Error: connection name not registered: %s the following are registered: %s"
                                              conn-name
                                              (vec (keys @*connection-registry*))))))
  (if-let [conn (get @*curr-thread-connections* conn-name)]
    [conn false]
    (let [new-connection (.getConnection #^BasicDataSource (get @*connection-registry* conn-name))]
      (swap! *curr-thread-connections* assoc conn-name new-connection)
      [new-connection true])))

(defn with-connection* [conn-name func]
  (let [helper-fn
        #(let [[conn we-opened-it] (get-connection conn-name)]
           (binding [clojure.contrib.sql.internal/*db*
                     (merge
                      clojure.contrib.sql.internal/*db*
                      {:connection conn
                       :level      (get clojure.contrib.sql.internal/*db* :level 0)
                       :rollback   (get clojure.contrib.sql.internal/*db* :rollback (atom false))})]
             (try
              (func)
              (finally
               (if we-opened-it
                 (do
                   (swap! *curr-thread-connections* dissoc conn-name)
                   (.close conn)))))))]
    (if (nil? *curr-thread-connections*)
      (binding [*curr-thread-connections* (atom {})]
        (helper-fn))
      (helper-fn))))


(defmacro with-connection [conn-name & body]
  `(with-connection* ~conn-name (fn [] ~@body)))

(defn- test-connection [name]
  (with-connection name true))


(defn register-connection! [name params]
  (Class/forName (:driver-class-name params))
  (let [connection-pool (doto (BasicDataSource.)
                          (.setDriverClassName (:driver-class-name params))
                          (.setUsername        (:user params))
                          (.setPassword        (:password params))
                          (.setUrl             (:url params))
                          (.setMaxActive       (:max-active params 8)))]
    (dosync
     (alter *connection-registry* assoc name connection-pool)
     (test-connection name))))


(defn with-retry* [num-retries retryable-error? body-fn]
  (loop [retries-left num-retries
         errors []]
    (if (zero? retries-left)
      (throw (rn.clorine.RetriesExhaustedException.
              (.concat "Retries Exhausted. :: "
                       (retries-exhausted-get-errors errors))
              errors))
      (let [ex     (atom nil)
            result (atom nil)]
        (try
         (reset! result (body-fn))
         (catch Exception e
          (reset! ex e)))
        (cond (not @ex)
              @result

              (retryable-error? @ex)
              (recur (dec retries-left) (conj errors @ex))

              :else
              (throw (rn.clorine.RetriesExhaustedException.
                      (format "UnRetriable Exception encoutered. %s/%s :: %s"
                              (class @ex)
                              (.getMessage @ex)
                              (retries-exhausted-get-errors errors))
                      @ex
                      errors)))))))


(defmacro with-retry [num-retries exception-predicate & body]
  `(with-retry* ~num-retries ~exception-predicate (fn [] ~@body) ))

