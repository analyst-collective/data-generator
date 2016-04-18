# Overview

This section refers to the **models** key in the general *config* structure below:

    {
        "storage": {
            "type": "storage type here",
            "spec": "storage spec here"
        },
        "models": {
            "model_number_1": {
                "model": {
                    "field_1": {
                        "details": "here"
                    },
                    "field_2": {
                        "details": "here"
                    }
                }
            },
            "model_number_2": {
                "...": "..."
            }
        }
    }

## High Level Details

* Each key top level key within the *models* object (seen above) corresponds to a table in the storage layer. The format and constraints of this name are enforced by the underlying storage.

* Each *table* has a *model* key with an object of *fields*.

* Each top level key within the *model* object (see above) corresponds to a column in that *table*. The format and constraints of this name are enforced by the underlying storage.

* The details within the *field* object are discussed below. In general, they determine how that column is generated for each row.

## Field Details

### Field Types

This tool has several internal field types. Each field type is specified in the field config like so:

    "field_1": {
        "type": "integer",
        "more": "details
    }
        

This tool normalizes the provided field type and in turn, each storage layer normalizes the field type for it's purposes. A mapping of all supported inputs -> normalized types is provided below:

*Note: Normalized types are the top level, possible config inputs are indented*

* integer
  - integer
  - int
* text (pre-specified lengths are currently ignored)
  - text
  - string
  - var
  - var (256)
  - varchar 
  - varchar (256)
* real
  - real
  - float
* double-precision
  - double-precision
  - double
* bigint
  - bigint
  - biginteger
* date
  - date
* timestamp-with-time-zone
  - timestamp with time zone
  - timestamp
  - datetime
* boolean
  - boolean
  - bool

There is one special field type, *association*. If a field is a foreign key to another table being generated, you specify it's type as *association* and the tool will determine the appropriate field type from that model's config.


### Master Field

Exactly **ONE** field per model must have a "master" object that controls how the table as a whole is generated. There are currently three options here:

1. Generated a fixed number of rows

2. Generate 0-N rows in this table *for each* row of another table in *this config*.

3. Generate a row in this table *for each* row of a provided "SQL query" within the database (can join multiple tables).

#### Fixed Number of Rows

Within a field (consider "field_1" above), you would provide a key/value as so:

    "master": {
        "count": 3000
    }

This would generate 3000 rows for that table. 3000 can be changed to any integer.


#### Generate 0-N rows from a table in this config

Within a field (consider "field_1" above), you would provide a key/value as so:

    "master": {
        "model": "model_number_2"
    }

The above config reads "For each row of model\_number\_2 that is created, create a row in this table. The field containing 

This is useful, but breaks down quickly for many datasets that don't have a 1:1 mapping of rows between tables. Things become more interesting when you add in additional keys next to "model".

* quantity
* probability
* foreach

##### Quantity

This lets you build 1:Many associations. The `quantity` value is any "Field Value Config" (see below) that can return an integer.

Here's an example for creating between 1 and 10 rows in this tab for each row of the *mode\_number\_1* table.

    "master": {
        "model": "model_number_1",
        "quantity": {
            "type": "range",
            "properties": {
                "min": 1,
                "max": 11
            }
        }
    }

You can read more about the details of various "Field Value Congfig's" below.

`quantity` can be used by itself, or in conjunction with `probability`. A quantity of `0` is valid and will result in no rows being created.

##### Probability

This lets you determine a likelyhood that *any* rows in this table will be created. This defaults to 1 (for 100%).

Here's an example that gives a 70% chance that we we create a row(s) in this table for the input row.

    "master": {
        "model": "model_number_1",
        "probability": 0.7
    }

`probability` can be used by itself, or in conjuction with `quantity`. A `probability` of `0` is the same as a `quantity` of `0` in that no rows in this table will be created.

##### Foreach

This lets you create a row(s) in table `model_number_1` not just for each row in `model_number_1`, but for each permutation of rows in multiple tables.

Let's say you have a `campaigns` table and a `list_members` table. For each campaign sent to a list, you want a single row in the `email` table. Here's what that might look like:

    "master": {
        "model": "campaigns",
        "foreach": {
            "model": "list_members"
        }
    }
    
The `foreach` block can also have a `filter` key/value. Using references (explained in detail below) you can specify which permutations to include/exclude. Here's an example of only including rows of `list_members` where the column `age` is greater than 18.

    "master": {
        "model": "campaigns",
        "foreach": {
            "model": "list_members",
            "filter": "$list_members.age > 18"
        }
    }

The `foreach` value can optionally be an array of objects to create a permutation between more than 2 tables. Be careful though, permutations with multiple large tables can get very large very fast!

`foreach` can be used with both `quantity` and `probability`.

#### Generate a row for each row in a SQL query

If you have a pre-existing data set that you'd like to extend with more tables, you can provide the `master` block with a sql query to "seed" this table with data. Here's an example of building an `orders` table from two existing "sub_tables" that had been generated previously.

    "master": {
        "type": "remote-query",
        "model": "dummy_name_for_query",
        "query": "select * from (select customer, \"type\", campaign, createdat from direct_orders UNION ALL select customer, \"type\", campaign, createdat from email_orders) as temp order by createdat;"
    }
    
In this scenario, we'll create a row for each return row from the query, and each "Field Value Config" (described below) will have access to the values of these returned rows.

### Field Values

[Details can be found here](field_values.md)
