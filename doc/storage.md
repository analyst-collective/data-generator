# Overview

This section refers to the **storage** key in the general *config* structure below:

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

Currently, this tool only supports Postgreql as an endpoint for the generated data. See the section below for details.


## Postgresql

The structure for this endpoint is as follows:

*Note: This entire object will be included **underneath** the "storage" key.*

    {
        "type": "postgresql",
        "spec": {
            "user": "insert your username here",
            "password": "insert your password here",
            "dbtype": "postgresql",
            "dbname": "insert the name of your database here",
            "host": "insert your host (possibly localhost) here",
            "port": "insert your port here (the standard is 5432)"
        }
    }

The *password* key can be ommitted if your user does not have a password.
