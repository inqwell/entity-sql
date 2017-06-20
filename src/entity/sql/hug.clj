(ns entity.sql.hug
  (:require [entity.core :refer [IO read-key]]
            [hugsql.core :as hugsql]
            [hikari-cp.core :refer [datasource-config]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.set :refer [rename-keys]]
            [clojure.java.jdbc :as jdbc])
  (:import [com.zaxxer.hikari HikariDataSource]
           (java.sql PreparedStatement)))

(defn- make-fn
  "Capture the invocation of a generated SQL statement, combining the
  common select statement and looking for any additional snippets required.
  The resulting function  takes two arguments
   - db-spec
   - any map that provides the necessary SQL parameter values"
  [fkey f meta fns select-stmt entity-opts hug-opts jdbc-opts]
  (let [date-cols-k (->> fkey
                         name
                         (str "date-cols-")
                         keyword)
        date-cols-f (get-in fns [date-cols-k :fn])
        date-cols (if date-cols-f
                    (-> (date-cols-f)
                        first
                        read-string
                        eval))]

    (fn [db-spec key-val]
      (let [result (f db-spec
                      (merge key-val
                             {:select-stmt select-stmt}
                             date-cols
                             entity-opts)
                      hug-opts
                      jdbc-opts)
            res-type (:result meta)]
        (if (#{:1 :one} res-type)
          (if (map? result) result nil)
          result)))))

(defn make-sql-fns
  "Process a SQL file turning its functions into ready-made queries. The
  file must include a snippet called select-stmt, which will be applied
  in usages of all queries.

    - entity-opts will be merged with the supplied parameter values when
      the function is called. These additional values may be used by
      SQL statements and the select snippet as required, for example to
      support SQL implementation specific choices.

    - hug-opts will be passed to the HugSQL generated function.

    - jdbc-opts will be passed to the HugSQL generated function, which
      in turn passes them to the underlying JDBC library call."
  ([sql-file] (make-sql-fns sql-file {} {} {}))
  ([sql-file entity-opts] (make-sql-fns sql-file entity-opts {} {}))
  ([sql-file entity-opts hug-opts] (make-sql-fns sql-file entity-opts hug-opts {}))
  ([sql-file entity-opts hug-opts jdbc-opts]
  (let [fns (hugsql/map-of-db-fns sql-file)
        select-stmt (or (get-in fns [:select-stmt :fn])
                        (throw (ex-info (str "No select-stmt found in " sql-file) {})))
        select-stmt (select-stmt entity-opts)
        fns (dissoc fns :select-stmt)]
    (reduce-kv (fn [m k v]
                 (if-not (get-in v [:meta :snip?])
                   (assoc m k (make-fn
                                k
                                (:fn v)
                                (:meta v)
                                fns
                                select-stmt
                                entity-opts
                                hug-opts
                                jdbc-opts))
                   m))
               {}
               fns))))

; Credit to conman
(defn make-config
  "Validate a pool specification and support some alternative spec keys"
  [{:keys [jdbc-url adapter datasource datasource-classname] :as pool-spec}]
  (when (not (or jdbc-url adapter datasource datasource-classname))
    (throw (ex-info "One of :jdbc-url, :adapter, :datasource, or :datasource-classname is required for a valid connection" {})))
  (datasource-config
    (-> pool-spec
        ;(format-url)
        (rename-keys
          {:auto-commit?  :auto-commit
           :conn-timeout  :connection-timeout
           :min-idle      :minimum-idle
           :max-pool-size :maximum-pool-size}))))



(defn connect!
  "Create a connection pool from the given pool-spec"
  [pool-spec]
  {:datasource (HikariDataSource. (make-config pool-spec))})

(defn disconnect!
  "Close the connection pool, if not already closed."
  [conn]
  (when-let [ds (:datasource conn)]
    (when-not (.isClosed ds)
      (.close ds))))

(defn- validate
  [sql-file entity-opts]
  (when-not (io/resource sql-file)
    (throw (Exception. (str "Cannot locate sql file " sql-file)))))

(deftype SqlIO [fns]
  IO
  (read-key [io key-val]
    (let [m (meta key-val)
          {:keys [key-name]} m]
      (read-key io key-name key-val)))
  (read-key [_ key-name key-val]
    (if-let [f (fns key-name)]
      (f key-val)
      (throw (ex-info "Unknown read key" {:key-name key-name
                                          :key-val  key-val
                                          :key-meta (meta key-val)}))))

  ;TODO: consider a dynamic var to enable checking fields conform with proto
  (write-val [_ entity-val]
    (if-let [f (:write fns)]
      (f entity-val)
      (throw (ex-info "Entity does not support write"
                      {:entity-val entity-val
                       :entity-meta (meta entity-val)}))))
  (delete-val [_ entity-val]
    (if-let [f (:delete fns)]
      (f entity-val)
      (throw (ex-info "Entity does not support delete"
                      {:entity-val  entity-val
                       :entity-meta (meta entity-val)})))))

(defmacro bind-connection
  "Bind a db connection returned from connect! to the collection
  of functions defined in sql-file, leaving the map of values to
  satisfy the query as the only parameter. If the connection is
  held in a dynamic variable it can be bound for use within a
  transaction.

  Options should be a nested map of entity-opts, hug-opts
  and jdbc-opts each of which is a map of options at that level.

  Returns an implementation of entity-core/IO."
  [conn sql-file options]
    (let [{:keys [entity-opts hug-opts jdbc-opts]
            :or   {entity-opts {}
                   jdbc-opts {:identifiers identity}
                   hug-opts {}}} (eval options)]
      (validate sql-file entity-opts)
      `(do
         (SqlIO. (reduce-kv (fn [m# k# v#]
                              (assoc m# k# (fn [key-val#]
                                             (v# ~conn key-val#))))
                            {}
                            (make-sql-fns ~sql-file ~entity-opts ~hug-opts ~jdbc-opts))))))

(defmacro with-transaction
  "Runs the body in a jdbc transaction using conn as the transaction connection.
   The isolation level and readonly status of the transaction may also be specified.
   (with-transaction [conn {:isolation level :read-only? true}]
     ... )
   See clojure.java.jdbc/db-transaction* for more details on the transaction
   options."
  [args & body]
  `(clojure.java.jdbc/with-db-transaction [conn# ~(first args) ~@(rest args)]
                                          (binding [~(first args) conn#]
                                            ~@body)))


