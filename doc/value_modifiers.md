# General

Modifiers allow you to provide a transformation to a value after it's been generated. These are usually more specific to the field type than field values are.

A modifier configurations can be found in the value config of `start_date_month`:

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
                    "start_date": {...},
                    "start_date_month": {
                        "type": "integer",
                        "value": {
                            "type": "enum",
                            "weights": {
                                "$$self.start_date": 1
                            },
                            "modifier": {
                                "format": "time-component",
                                "function": "month"
                            }
                        }
                    }
                }
            },
            "model_number_2": {
                "...": "..."
            }
        }
    }


## Modifier Formats

### Time-Component

While it's possible to extract components of a timestamp with math in *formula* fields ([see formula values][field_values.md]) this can be challenging to do accurately as a result of the many idiosyncrasies with time. To faciliate accurate extracting of components, a Clojure library's (clj-time) functions are exposed. [The full API documentation can be found here][http://clj-time.github.io/clj-time/doc/clj-time.core.html#var-DateTimeProtocol]. If a value returns an integer or biginteger, this modifier will coerce the result into a `DateTime` object and call the specified function on it.

Here's the config for extracting the "day of the week":

    "modifier": {
        "format": "time-component",
        "function": "day-of-week"
    }

Here's the config for extracting the hour:

    "modifier": {
        "format": "time-component",
        "function": "hour"
    }
