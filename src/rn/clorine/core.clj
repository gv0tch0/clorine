(ns rn.clorine.core
  (:require [clojure.contrib.pprint   :as pp]
            [clojure.contrib.sql      :as sql])
  (:import [org.apache.commons.dbcp  BasicDataSource]))

(def *connection-registry* (atom {}))

(def *curr-thread-connections* nil)

(defn register-connection! [name params]
  (if (get @*connection-registry* name)
    (throw (RuntimeException. (format "Connection %s already registered!" name))))
  (let [connection-pool (doto (BasicDataSource.)
                          (.setDriverClassName (:driver-class-name params))
                          (.setUsername        (:user params))
                          (.setPassword        (:password params))
                          (.setUrl             (:url params))
                          (.setMaxActive       (:max-active params 8)))]
    (swap! *connection-registry* assoc name connection-pool)))


(defn get-connection [conn-name]
  (if-let [conn (get @*curr-thread-connections* conn-name)]
    [conn false]
    (do
      (let [new-connection (.getConnection (get @*connection-registry* conn-name))]
       (swap! *curr-thread-connections* assoc conn-name new-connection)
       [new-connection true]))))

(defn with-connection* [conn-name func]
  (let [helper-fn
        #(let [[conn we-opened-it] (get-connection conn-name)]
           (binding [clojure.contrib.sql.internal/*db*
                     (assoc clojure.contrib.sql.internal/*db*
                       :connection conn :level 0 :rollback (atom false))]
             (try
              (func)
              (finally
               (if we-opened-it
                 (.close conn))))))]
    (if (nil? *curr-thread-connections*)
      (binding [*curr-thread-connections* (atom {})]
        (helper-fn))
       (helper-fn))))


(defmacro with-connection [conn-name & body]
  `(with-connection* ~conn-name (fn [] ~@body)))

