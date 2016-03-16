# data-generator

This is a tool for generating demonstration datasets that conform to a specified shape or pattern.

Want to demonstrate data analysis (graphing, ML, etc.) but don't have data that you can share? This tool is for you.

This tool can be used as a standalone JAR or as a library in any JVM language.

## Installation

1) Clone this repo

2) Ensure you have Leinigan (and any of it's dependencies) installed. See here https://github.com/technomancy/leiningen

3) For standalone access run `lein uberjar` to build. The jar will be located in `/target/uberjar/` and have a suffix of `-standalone.jar`

## Usage

### Standalone

    $ java -jar data-generator-X.X.X-standalone.jar <filename of config here>

Note: Currently only supports json files

### Programmatic

`(data-generator.core/generate-data <insert config MAP here>)`


