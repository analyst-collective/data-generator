# General

This explains the configurations for generating *values*. Usually this is with regard to *values* for a field, but they're also used for the `quantity` block in `master` blocks ([see here](fields.md)) and elsewhere.

Field value configurations will be found as the values to the `field_1` and `field_2` keys below:

    {
        "storage": {
            "type": "storage type here",
            "spec": "storage spec here"
        },
        "models": {
            "model_number_1": {
                "model": {
                    "field_1": {
                        "type": "integer",
                        "value": {
                            "details": "here"
                        }
                    },
                    "field_2": {
                        "type": "string",
                        "value": {
                            "details": "here"
                        }
                    }
                }
            },
            "model_number_2": {
                "...": "..."
            }
        }
    }

Another, related topic is *value modifiers*. These are covered in more detail [here](value_modifiers.md).

## Special Field Configs

### Primarykeys

    "field_1": {
        "type": "integer",
        "primarykey": true,
        "value": {...}
    }

One field must have this designation per table if *any* table is associated to it. It's good practice to have one for every table period though.

### Virtual Fields

Sometimes it's helpful to calculate an "intermediary field" on a table that you won't care about after the table is generated. Let's say you have an `items` table with a `popularity` column that's used to determine how many `orders` include this each item. You probably don't want to leave the `popularity` column in the `items` table after the `orders` table is done being generated, because that's not a very "realistic" column to have in a table. To auto-drop the `popularity` column when you're done generating data do this:

    "popularity": {
        "type": "integer",
        "virtual": true,
        "value": {...}
    }

## Field Value Configs

All `value` block results will be coerced to the `type` specified (integer, text, double-precision, etc.). [See here for info on field types](fields.md)


### Formulas

    "value": {
        "type": "formula",
        "properties": {
            "equation": "3 + 2 * 56"
        }
    }

Optionally another key/value `"randomness": 0.2` can be included next to `equation`. If present, this will randomlly skew the calcualted value +/- the fraction provided. `"randomness": 0.2` is equivalent to +/- 20%

The above trivial configuration would generate a value of 115 for `field_1`. While not useful by itself, this becomes powerful when the equation makes use of the *references* system (see below). An example equation could be:

    "3 + 2 * $$self.field_2"

Read on for more details about the *references* system.

For the purposes of formulas, any referenced `date` or `timestamp-with-time-zone` will be considered as it's *epoch* in milliseconds.

Adding 24 hours to a timestamp looks like this:

    "$$self.signup_date + 86400000"

Formulas can contain parenthesis, but they may **NOT** be nested.

Valid:

    "(3 + 2) * 4"


Invalid:

    "(3 + (3 * 2)) * 4"

### Distribution

    "value": {
        "type": "distribution",
        "properties": {
            "type": "normal-distribution",
            "args": [0, 1]
        }
    }

The `type` under properties can be anything implemented by the following library: https://github.com/incanter/incanter/wiki/probability-distributions

The syntax for distrubtions is found here (normal-distribution used for demonstration) https://github.com/incanter/incanter/blob/master/modules/incanter-core/src/incanter/distributions.clj#L470

`args` is optional as all distrubtions have defaults for any arguments.

The above config samples a normal distrubiton with a mean of `0` and a standard deviation of `1` to generate values.

Arguments can use the *references* system. For example:

    "args": ["$$self.field_2", 1]

### Generic "Fake Values"

There exists in most languages  a library for generating arbitrary fake values (popularized by the Perl version?). The Clojure port of this library can be referenced in your configs.

https://github.com/paraseba/faker

    "value": {
        "type": "faker",
        "function": "internet.email",
        "args": ["$$self.name"]
    }

The above config will execute the `email` function in the `internet` namespace of the library to generate this value. As shown, the optional `args` key can use the *references* system (see below).

### Random Number Within A Range

    "value": {
        "type": "range",
        "properties": {
            "min": 3,
            "max": 10
        }
    }

The config above will generate a random `double` between 3 (inclusive) and 10 (exclusive). The numbers supplied to `min` and `max` can utilize the *references* system (see below).

### Enumeration

    "value": {
        "type": "enum",
        "weights": {
            "$$self.name": 5,
            "John": 3,
            "Unknown": "$$model_1.mystery_level"
        }
    }

The config above will generate a value from the list of enumerations with a likelihood determined by the weights. 

Each **key** is a possible value, and it's **value** is it's weighting. Notice how both **keys** and **values** can be given a specific value or a *reference* (see below). While JSON requires **keys** to be strings, these will be coerced to the proper value type. The following config will always return a boolean `true` or `false`, not a string.

    "field_1": {
        "type": "boolean",
        "value": {
            "type": "enum",
            "weights": {
                "true": 7,
                "false": 3
            }
        }
    }

### Autoincrement

Self explainitory! This is generated by the underlying storage layer so it can *only* be used for field values.

### Concatonate

For combining string values into a single value.

    "value": {
        "type": "concat",
        "properties": {
            "values": ["$$self.first_name", " ", "$$self.last_name"]
        }
    }

The config above would be useful for creating a `full_name` column in a table that also had `first_name` and `last_name`. As shown, The values array uses the *references* system (see below).

### UUID

For creating a UUID according to [this specification][https://en.wikipedia.org/wiki/Universally_unique_identifier#Version_4_.28random.29]

    "value": {
        "type": "uuid"
    }

### Case Statements

This value type allows you to add arbitrarily nested branching logic in a value config.

    "value": {
        "type": "case",
        "check": "$$self.age > 18",
        "branches": {
            "true": {...},
            "false": {...}
        }
    }

In the above config, `check` is evaluated just like any equation provided in a `formula` value block (see above). The calcualted result is used to decide which "sub-value-block" (the keys in `branches`) to use. The "sub-value-blocks" are just like any other value block with no restrictions. They can even be another `case` value block!

*Note: Currently the `branches` **keys** can be any value type. If the `check` formula calculates a number, the **keys** will be decimals (known issue). For instance `"check": "2 + 3"` would require a branch of "2.0": {...}. This is a known issue. A check can return a string if it's a reference to a string field, this is useful for providing branching logic to an `enum` field.*
        

## References

When generating values, often it's helpful to refer to other values, either within the same row, within an associated model that's used to generate this table ([see here](fields.md)). To accomplish this, special syntax to *reference* these details is used.

* Field on this row
* Field on associated row
* Special context information

### Field on this row

    "model": {
        "field_1": {
            "type": "integer",
            "value": {
                "type": "range",
                "properties": {
                    "min": 1,
                    "max": 11"
                }
            }
        },
        "field_2": {
            "type": "integer",
            "value": {
                "type": "formula",
                "properties": {
                    "equation": "3 + $$self.field_1"
                }
            }
        }
    }


The special syntax `$$self` signifies that this is a placeholder for whatever value is generated for another field on this row of this table. `$$self.field_1` will be replaced with whatever the config generates for field_1 of this particular row. In this case, `field_1` will be a randomly generated integer between 1 and 11, for each row, field_2 will be whatever that number is, plus 3.

Please note the **TWO** `$` symbols in front of the word `self` (`$$self`). This is important to differentiate from the syntax introduced next.

**The order in which you define fields does not matter. This tool will generate them in the correct order.**

### Field on associated row

    "models": {
        "model_a": {
            "model": {
                "id": {
                    "type": "integer",
                    "primarykey": 1,
                    "value": {
                        "type": "autoincrement"
                    }
                },
                "field_a2": {...}
            }
        },
        "model_b": {
            "model": {
                "field_b1": {
                    "type": "enum",
                    "weights": {
                        "$model_a.field_a2": 5,
                        "Hello": 5
                    }
                },
                "field_b2": {
                    "type": "association",
                    "master": {
                        "model": "model_a"
                    }
                }
            }
        }
    }

In the config above, a row of `model_b` is generated for each row of `model_a` ([see here for more details](fieldsmd)). `field_b2` will be the primary key value of `model_a` for each row generated (a 1:1 association foreign key).

By associating these two models, each row of `model_b` has access to *all values* of the associated row in `model_a`. The syntax for referencing these values is shown in the config above and reiterated below:

    "$model_a.field_a1"
    
In the above config, each `field_b1` in `model_b` will have a 50% chance of being `Hello` and a 50% chance of being whatever the associated `model_a` field `field_a2` is. In this way, logic can be built *between* tables for associated rows.

### Special Context Information

There are a few more values that can be *referenced* based on special contexts.

* The "count" of a row if generated based on a count or an association to a table that was
* The "count" of a row within a 1:Many relationship
* The total number of the "Many" within a 1:Many relationship
* Values on rows which share an association within a 1:many relationship


#### Counts

If a row is generated with this `master` syntax ([see here for me detail](fields.md)):

    "master": {
        "count": 200
    }

The number (0-199, inclusive) can be referenced by the variable `x` in the same way `$$self.field` and `$model.field` work.

If I want a row per day in a table starting 4/15/2016 at 21:12:05 UTC, here's an example field making use of this:

    "day": {
        "type": "datetime",
        "value": {
            "type": "formula",
            "properties": {
                "equation": "1460754726000 + x * 86400000"
            }
        }
    }

1460754726000 is the starting timestamp in epoch milliseconds
86400000 is 1000 milliseconds/second * 60 seconds/minute * 60 minutes/hour * 24 hours/day
`x` is the "day count"

`x` can be referred to in any field on a model with this `master` syntax and any associated models as well. This is useful for building "cohorts".

#### 1:Many Relationships

When a row from one table is associated to *multiple* rows on another table, it can sometimes be helpful to access how many associated rows there are, and which one of those rows is currently being generated. The total quantity of rows is accessible by the variable `z` and which row is currently being generated is accessible by the variable `y`. Below is an example of this:

    "value": {
        "type": "case",
        "check": "z - 1 == y",
        "branches": {
            "true": {...},
            "false": {...}
        }
    }

The above config allows you to use one value config for each row *except* the last (the `false` branch) and another for the last associated row (the `true` branch). Notice that since `y` is "zero indexed", `z - 1` must be used to identify the last row.

When generating a 1:Many relationship between tables, sometimes it's useful to "chain" values together from one row to the next of the "Many" table. Consider a `subscriptions` table and an `invoices` table. Each subscription can have many invoices. In this scenario the `start_date` of a given invoice for a subscription will match the `end_date` of the previous invoice for that same subscription. Here is an example of how the syntax works for this tool:

    "end_date": {...},
    "start_date": {
        "type": "datetime",
        "value": {
            "type": "case",
            "check": "y == 0",
            "branches": {
                "true": {...},
                "false": {
                    "type": "formula",
                    "properties": {
                        "equation": "$$mult$invoices$(y - 1).end_date"
                    }
                }
            }
        }
    }

The `$$mult` identifier is used to access another row from the same table that shares an associated record (a particular subscription in this case). The `$(y - 1)` section indicates which row of a shared association we want (in this case, "the previous row"). Finally, `.end_date` is used to access the specific column, just as we usually do.

*Note: Due to a known issue, the index used **MUST** be a double (0.0 for the first postion, 1.0 for the second, etc). Formula values evaluate to a double by default, so the example above is correct.*
