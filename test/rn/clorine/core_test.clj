(ns rn.clorine.core-test
  (:require [rn.clorine.core :as cl]
            [clojure.contrib.sql :as sql])
  (:use [clojure.test])
  (:import [java.sql SQLException]))

(def *db-spec*
     {:driver-class-name "org.sqlite.JDBC"
      :url "jdbc:sqlite:./clorine-test.sqlite"})


(defn db-connect-fixture [f]
  (cl/register-connection! :clorine-test  *db-spec*)
  (cl/with-connection :clorine-test
    (f)))


(defn db-test-fixtures [f]
  (sql/do-commands "DROP TABLE if exists chickens")
  (sql/create-table :chickens  [:name :text "UNIQUE"])
  (f))



(use-fixtures :each db-connect-fixture db-test-fixtures)


(defn is-sql-exception? [e]
  (let [retriable? #{SQLException}]
    (or
     (retriable? (class e))
     (retriable? (class (.getCause e))))))

(deftest test-exhausted-retries
  (cl/with-connection :clorine-test
    (sql/insert-values :chickens [:name] ["Dinner"]))

  (is (thrown-with-msg?
        rn.clorine.RetriesExhaustedException
        #"Retries Exhausted"
        (cl/with-retry 5
          is-sql-exception?
          (cl/with-connection :clorine-test
            (sql/insert-values :chickens [:name] ["Dinner"]))))))


(deftest test-retry-works
  (cl/with-connection :clorine-test
    (sql/insert-values :chickens [:name] ["Dinner"])

    (is (= true
           (let [first-time? (atom true)]
             (cl/with-retry 5
               is-sql-exception?
               (if @first-time?
                 (do
                   (reset! first-time? false)
                   (cl/with-connection :clorine-test
                     (sql/insert-values :chickens [:name] ["Dinner"])))
                 (do
                   (cl/with-connection :clorine-test
                     (sql/delete-rows :chickens ["name=?" "Dinner"])
                     (sql/insert-values :chickens [:name] ["Dinner"])))))
             true)))))


(comment
  (run-tests 'rn.clorine.core-test)
  )