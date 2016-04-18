# Introduction to data-generator

**data-generator** reads in a *config* with a specific format and outputs data according to the provided information. The output format can be generalized as an [relational data model](https://en.wikipedia.org/wiki/Relational_model).

This *config* file specifies several things:

* What **tables** of data to produce
* What **columns** each table has
* How to generate a value for a specific table/column/row:
  - Can be based on other columns within the row
  - Can be based on other rows
  - Can be based on other tables


The basic structure of a config (shown here as JSON) is as follows:


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

For more details on the **storage** portion of the config, [check out here][storage.md].

For more details on field level details, [check out here][fields.md].

For a full example, check out the `/resources` folder in this repo.
