
(ns entity.sql.hug-test
  (:require [clojure.test :refer :all]
            [entity.sql.hug :refer :all]
            [clojure.java.jdbc :as sql]
            [clojure.java.io :as io])
  (:import [entity.sql.hug SqlIO]))

(defonce ^:dynamic conn
         {:connection-uri "jdbc:h2:./test.db"
          ;:make-pool?     true
          ;:naming         {:keys   clojure.string/lower-case
          ;                 :fields clojure.string/upper-case}
          })

(def io (bind-connection conn "test.sql" {:entity-opts
                                          {:server-type :h2}}))

(def strawberry {:Fruit "Strawberry"
                 :Description "Soft Summer Fruit"
                 :ShelfLife 14
                 :Active 1
                 :Freezable "Y"})

(def upd-strawberry {:Fruit "Strawberry"
                 :Description "Soft Summer Fruit"
                 :ShelfLife 28  ; Long life strawberries
                 :Active 1
                 :Freezable "Y"})

(def banana {:Fruit "Banana"
             :Description "Yellow and not straight"
             :ShelfLife 21
             :Active 1
             :Freezable "N"})

(def pineapple {:Fruit       "Pineapple"
                :Description "Edibe Bromeliad"
                :ShelfLife   18
                :Active      0    ; Out of season
                :Freezable   "N"})

(def bad-apple {:Fruit       "BadApple"
                :Description "Always one"
                :ShelfLife   0
                :Active      "Y"  ; error here
                :Freezable   "Y"})

(def filter-key ^{:key-name :filter} {:Fruit        nil
                                      :Active       nil
                                      :Freezable    nil
                                      :MinShelfLife 14
                                      :MaxShelfLife 21})

(def by-active-key ^{:key-name :by-active} {:Active 0})

(def pk-strawberry ^{:key-name :primary} {:Fruit "Strawberry"})

;(.delete-val io strawberry)



(defn delete-test-db []
  (io/delete-file "test.db.mv.db" true)
  (io/delete-file "test.db.trace.db" true))

(defn create-test-table []
  (sql/db-do-commands
    conn
    "DROP TABLE Fruits IF EXISTS;"
    (sql/create-table-ddl
      :Fruit
      [[:Fruit "VARCHAR(32)" "PRIMARY KEY"]
       [:Description "VARCHAR(32)"]
       [:ShelfLife :int]
       [:Active :int]
       [:Freezable "CHAR(1)"]]
      {:table-spec ""})))

(use-fixtures
  :once
  (fn [f]
    (delete-test-db)
    (create-test-table)
    (f)))

(deftest write
  (testing "write two instances"
    (is (= 2
           (do
             (.delete-val io pineapple) ; only uses the PK anyway
             (.write-val io  banana)
             (.write-val io  strawberry)
             (count (.read-key io :all {})))))))

(deftest read-filter
  (testing "filter expect two"
    (is (= (list banana strawberry)
           (do
             (.delete-val io pineapple) ; only uses the PK anyway
             (.write-val io banana)
             (.write-val io strawberry)
             (.read-key io filter-key))))))

(deftest read-filter-one
  (testing "filter expect one"
    (is (= (list banana)
           (do
             (.delete-val io pineapple) ; only uses the PK anyway
             (.write-val io banana)
             (.write-val io strawberry)
             (.write-val io upd-strawberry)
             (.read-key io filter-key))))))

(deftest delete
  (testing "delete"
    (is (= (list banana)
           (do
             (.delete-val io pineapple) ; only uses the PK anyway
             (.write-val io banana)
             (.write-val io strawberry)
             (.delete-val io upd-strawberry) ; only uses the PK anyway
             (.read-key io filter-key))))))


(deftest read-inactive
  (testing "key by-active"
    (is (= (list pineapple)
           (do
             (.delete-val io strawberry) ; only uses the PK anyway
             (.delete-val io banana) ; only uses the PK anyway
             (.delete-val io pineapple) ; only uses the PK anyway
             (.write-val io banana)
             (.write-val io strawberry)
             (.write-val io pineapple)
             (.read-key io by-active-key))))))

(deftest transaction-rollback
  (do
    (.delete-val io strawberry)
    (.delete-val io banana)
    (.delete-val io pineapple))

  (with-transaction
    [conn]
    (sql/db-set-rollback-only! conn)
    (is
      (= 1
         (.write-val io strawberry)))
    (is
      (= strawberry
         (.read-key io pk-strawberry))))
  (is
    (nil? (.read-key io pk-strawberry))))

(deftest transaction-commit
  (do
    (.delete-val io strawberry)
    (.delete-val io banana)
    (.delete-val io pineapple)
    (.write-val io strawberry))

  (with-transaction
    [conn]
    (is
      (= 1
         (.write-val io upd-strawberry)))
    (is
      (= upd-strawberry
         (.read-key io pk-strawberry))))
  (is
    (= upd-strawberry (.read-key io pk-strawberry))))

(deftest transaction-abort
  (do
    (.delete-val io strawberry)
    (.delete-val io banana)
    (.delete-val io pineapple)
    (.write-val io strawberry))

  (try
    (with-transaction
      [conn]
      (is
        (= 1
           (.write-val io upd-strawberry)))
      (.write-val io bad-apple))
    (catch Exception _))

    (is
      (= strawberry (.read-key io pk-strawberry))))

; Credit conman
(deftest transaction-options
  (with-transaction
    [conn {:isolation :serializable}]
    (is (= java.sql.Connection/TRANSACTION_SERIALIZABLE
           (.getTransactionIsolation (sql/db-connection conn)))))
  (with-transaction
    [conn {:isolation :read-uncommitted}]
    (is (= java.sql.Connection/TRANSACTION_READ_UNCOMMITTED
           (.getTransactionIsolation (sql/db-connection conn))))))

