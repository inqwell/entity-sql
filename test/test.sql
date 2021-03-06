-- Fruit

-- :snip select-stmt
SELECT
 F.Fruit          AS "Fruit",
 F.Description    AS "Description",
 F.ShelfLife      AS "ShelfLife",
/*~
(if (= (:server-type params) :h2)
 "F.Active"
 "F.Activo")
~*/ AS "Active",
 F.Freezable      AS "Freezable"
FROM Fruit F

-- :name primary :? :1
:snip:select-stmt
WHERE F.Fruit = :Fruit

-- :name write :! :n
MERGE INTO Fruit
VALUES (:Fruit, :Description, :ShelfLife, :Active, :Freezable)

-- :name delete :! :n
DELETE FROM Fruit
WHERE Fruit = :Fruit

-- :name by-active :? :*
:snip:select-stmt
WHERE Active = :Active

-- :name filter :? :*
:snip:select-stmt
WHERE (Fruit = :Fruit OR :Fruit IS NULL)
AND (Active = :Active OR :Active IS NULL)
AND (Freezable = :Freezable OR :Freezable IS NULL)
AND (ShelfLife >= :MinShelfLife OR :MinShelfLife IS NULL)
AND (ShelfLife <= :MaxShelfLife OR :MaxShelfLife IS NULL)

-- :name all :? :*
:snip:select-stmt
