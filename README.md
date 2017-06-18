# entity-sql

Provides SQL persistence for entities defined using `entity-core` via
`HugSQL`. For further explanation of the terms _snippets_ ... please
see the HugSQL documentation.

The goal is to support a common, reusable select statement and to
provide support for database vendor independence. Database connections
are pooled using HikariCP.

## Basics
The concept defined by `entity-core` is that any persistence mechanism
will provide instances of that entity and that entity only. If 'joins'
are required these are achieved by placing the related entity (or
collection of entities if the relationship is 1:many) in a suitable
data structure, with their positions implying the relationship.

For example, a `Fruit` may have many `Supplier`s. A suitable structure
expressing this relationship might be:

```clojure
{:Fruit f
 :suppliers
 [{:Supplier s} {:Supplier s} ... ]}
```

If a `Fruit` has one `NutritionInfo` then this structure could express
that relationship like this:

```clojure
{:Fruit f :NutritionInfo n
 :suppliers
 [{:Supplier s} {:Supplier s} ... ]}
```

These structures are a matter for domain-level code.

## SQL and Keys
Each entity requires a `HugSQL` file structured in a certain way.

### Select Statement
The file must contain a snippet called `select-stmt` as in this example
```SQL
-- Fruit

-- :snip select-stmt
SELECT
 F.Fruit          AS "Fruit",
 F.Description    AS "Description",
 F.ShelfLife      AS "ShelfLife",
 F.Active         AS "Active",
 F.Freezable      AS "Freezable"
FROM Fruit F
```

### Primary Key
The query by the entity's primary key must be called `primary` like so:
```SQL
-- :name primary :? :1
:snip:select-stmt
WHERE F.Fruit   = :Fruit
```
Notice it uses the `select-stmt` snippet defined earlier.

### Other Keys
Other keys are defined similarly, for example:
```sql
-- :name by-freezable :? :*
:snip:select-stmt
WHERE F.Freezable = :Freezable
```
Note that a key's uniqueness must be reflected in the HugSQL
result type: a unique key should have a result type of `:1` and
a non-unique one a result type of `:*`.

Note also the function name must be the same as the key name used
in the (entity-core) definition.

### Support for Multiple Vendors
A SQL connection and a file of SQL statements are connected
using `bind-connection`. This macro includes
three sets of options. The set `entity-opts` are merged with the
parameter map passed to the generated HugSQL functions, making
them available for use in the SQL file.

Let's say you have developed your schema under one database vendor,
only to find a frequently used column name is a reserved word when
porting to another, eg:

```sql
-- :snip select-stmt
SELECT
 F.Fruit          AS "Fruit",
 F.Description    AS "Description",
 F.ShelfLife      AS "ShelfLife",
 F.Active         AS "Active",
 F.Freezable      AS "Freezable",
/*~
(if (= (:server-type params) :oracle)
 "F.User_"
 "F.User")
~*/ AS "User"
FROM Fruit F
```

By including the parameter `:server-type` in `entity-opts` it can be
used, in this case, within a Clojure expression (see HugSQL
documentation for further details).

### Updating
To write an entity instance, all fields must be written, and the
function must be called `:write`. Here is an example, taking into
account vendor variations:

```sql
-- :name write :! :n
/*~
(condp = (:server-type params)
 :mysql "
REPLACE Fruit
SET
    Fruit           = :Fruit,
    Description     = :Description,
    ShelfLife       = :ShelfLife,
    Active          = :Active,
    Freezable       = :Freezable
    User            = :User"
 :oracle "
MERGE INTO Fruit USING DUAL
ON (Fruit   = :Fruit)
WHEN MATCHED THEN
    UPDATE SET
        Description     = :Description,
        ShelfLife       = :ShelfLife,
        Active          = :Active,
        Freezable       = :Freezable
        User            = :User"
WHEN NOT MATCHED THEN
INSERT
(
    Fruit,
    Description,
    ShelfLife,
    Active,
    Freezable,
    User_
)
VALUES
(
    :Fruit,
    :Description,
    :ShelfLife,
    :Active,
    :Freezable,
    :User
)")
~*/
```

### Deletion
Deletion is optional, but if supported the HugSQL function must
be called `:delete`:

```sql
-- :name delete :! :n
DELETE FROM Fruit F
WHERE F.Fruit   = :Fruit
```

## Making Connections
In Clojure JDBC a 'connection' is only a map containing various
keys. If these keys only describe a URL, user and password
a connection to the underlying database will be made for each query.
entity-sql supplies the `:datasource` key using HikariCP as the
connection pool.

## Usage

Not presently hosted. Use `lein install` to place in your local maven
repo for now and use the version found in `project.clj` for your
dependency.

## License

Copyright © 2017 Inqwell Ltd

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
