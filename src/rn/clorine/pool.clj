(ns rn.clorine.pool
  (:import
   [org.apache.commons.pool PoolableObjectFactory]
   [org.apache.commons.pool.impl GenericObjectPool])
  (:use
   [clj-etl-utils.lang-utils :only [raise]]))


(defonce *registry* (atom {}))

(defn register-pool [pool-name factory-impl]
  (swap! *registry*
         assoc pool-name
         {:name    pool-name
          :factory factory-impl
          :pool    (GenericObjectPool. factory-impl)}))

(defn unregister-pool [pool-name]
  (swap! *registry*
         dissoc pool-name))

(defn make-factory [factory-fns-map]
  (if-not (contains? factory-fns-map :make-fn)
    (raise "Error: you must supply at least a :make-fn, but you can supply any of: [:activate-fn :destroy-fn :passivate-fn :validate-fn]"))
  (let [no-op (fn [this #^Object obj] nil)
        factory-fns-map (merge {:activate-fn  no-op
                                :destroy-fn   no-op
                                :passivate-fn no-op
                                :validate-fn  no-op}
                               factory-fns-map)
        {make-fn :make-fn activate-fn :activate-fn destroy-fn :destroy-fn passivate-fn :passivate-fn validate-fn :validate-fn} factory-fns-map]
    (reify
     org.apache.commons.pool.PoolableObjectFactory
     (#^void activateObject [this #^Object obj]
             (activate-fn this obj))
     (#^void destroyObject [this #^Object obj]
             (destroy-fn this obj))
     (#^Object makeObject [this]
               (make-fn this))
     (#^void passivateObject [this #^Object obj]
             (passivate-fn this obj))
     (#^boolean validateObject [this #^Object obj]
                (passivate-fn obj)))))

(defn get-registered-pool [pool-name]
  (@*registry* pool-name))

(defn with-instance* [pool-name body-fn]
  (let [registered-pool         (@*registry* pool-name)
        pool                    (:pool registered-pool)
        instance (.borrowObject pool)]
    (try
     (body-fn instance)
     (finally
      (.returnObject pool instance)))))

(defmacro with-instance [[inst-name pool-name] & body]
  `(with-instance* ~pool-name
     (fn [~inst-name]
       ~@body)))

