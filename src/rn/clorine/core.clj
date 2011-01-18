(ns rn.clorine.core
  (:require [clojure.contrib.pprint   :as pp]
            [clojure.contrib.sql      :as sql]
            [rn-db.core      :as db])
  (:import [org.apache.commons.dbcp  BasicDataSource]))

(defonce *connection-registry* (ref {}))

(def *curr-thread-connections* nil)


(defn get-connection [conn-name]
  (if-let [conn (get @*curr-thread-connections* conn-name)]
    [conn false]
    (let [new-connection (.getConnection (get @*connection-registry* conn-name))]
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
  (let [connection-pool (doto (BasicDataSource.)
                          (.setDriverClassName (:driver-class-name params))
                          (.setUsername        (:user params))
                          (.setPassword        (:password params))
                          (.setUrl             (:url params))
                          (.setMaxActive       (:max-active params 8)))]
    (dosync
     (alter *connection-registry* assoc name connection-pool)
     (test-connection name))))

