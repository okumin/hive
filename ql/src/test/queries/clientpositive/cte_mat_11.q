--! qt:dataset:src

set hive.optimize.cte.materialize.threshold=1;
set hive.optimize.cte.materialize.full.aggregate.only=false;

EXPLAIN CBO
WITH materialized_cte AS (
  SELECT * FROM src WHERE key = '100'
),
another_materialized_cte AS (
  SELECT * FROM src WHERE key = '100'
)
SELECT * FROM materialized_cte
UNION ALL
SELECT * FROM another_materialized_cte;

WITH materialized_cte AS (
  SELECT * FROM src WHERE key = '100'
),
another_materialized_cte AS (
  SELECT * FROM src WHERE key = '100'
)
SELECT * FROM materialized_cte
UNION ALL
SELECT * FROM another_materialized_cte;
