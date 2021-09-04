Kafka Console (kc)
------------------

- [Summary](#summary)
- [Downloads](#downloads)
- [Distribution](#distribution)
- [Requirements](#requirements)
- [Commands](#commands)
  * [Common parameters](#common-parameters)
    + [For all commands](#for-all-commands)
      - [--quiet](#--quiet)
    + [For all commands accessing Kafka](#for-all-commands-accessing-kafka)
      - [--bootstrap-servers=\<Kafka bootstrap server list>](#--bootstrap-servers---kafka-bootstrap-server-list-)
    + [For all commands reading Kafka topics](#for-all-commands-reading-kafka-topics)
      - [--schema-registry=\<AVRO schema registry url>](#--schema-registry---avro-schema-registry-url-)
    + [For all commands fetching data](#for-all-commands-fetching-data)
      - [-f=\<filter expression>, --filter=\<filter expression>](#-f---filter-expression-----filter---filter-expression-)
      - [-p=\<projection expression>, --projection=\<projection expression>](#-p---projection-expression-----projection---projection-expression-)
      - [-t=\<poll timeout>](#-t---poll-timeout-)
      - [-k=\<key format>](#-k---key-format-)
      - [-v=\<value format>](#-v---value-format-)
      - [--key-schema=\<key schema>](#--key-schema---key-schema-)
      - [--value-schema=\<value schema>](#--value-schema---value-schema-)
      - [-n=<message count>](#-n--message-count-)
  * [topics (t)](#topics--t-)

<small><i><a href='http://ecotrust-canada.github.io/markdown-toc/'>Table of contents generated with markdown-toc</a></i></small>

# Summary

Kafka Console is a command-line utility to make some **read-only** operations:
* to fetch messages from Kafka topics within a given time window filtered by Groovy expressions. Output messages could be transformed using Groovy expression too
* to select messages between offsets
* to get current offset information (including starting date of the first offset)
* to get topic listing

# Downloads
[Releases](https://github.com/dzmauchy/kc/releases)

# Distribution

Kafka Console is distributed as .zip file. After unpacking it, the following directory layout is present:
* bin
  * kc (executable for any Unix-like OS)
  * kc.bat (executable for Windows)
* lib
  * ... (jar files)
* conf
  * README.txt
  * (any files to be added to the classpath)

# Requirements

Java 11 or higher

# Commands

## Common parameters

### For all commands

#### --quiet

If specified, disables additional output to stderr.

### For all commands accessing Kafka

#### --bootstrap-servers=\<Kafka bootstrap server list>
Comma-separated list of the Kafka bootstrap servers.
|Default|Value|
|-|-|
|1st default|KAFKA_BOOTSTRAP_SERVERS environment variable|
|2nd default|localhost:9092|

### For all commands reading Kafka topics

#### --schema-registry=\<AVRO schema registry url>
AVRO schema registry url with slash at the end, e.g. ```http://my-server.com/```
|Default|Value|
|-|-|
|1st default|SCHEMA_REGISTRY environment variable|
|2nd default|http://localhost:2181/|

### For all commands fetching data

#### -f=\<filter expression>, --filter=\<filter expression>

A groovy expression to filter incoming records.

|Default|Value|
|-|-|
|1st default|```true```|

#### -p=\<projection expression>, --projection=\<projection expression>

A groovy expression to map the filtered records to an appropriate structure.

|Default|Value|
|-|-|
|1st default|```[t: $r.topic(), p: $r.partition(), o: $r.offset(), k: $k, v: $v]```|

#### -t=\<poll timeout>

Poll timeout.
Default value is PT5S (5s).
See [ISO duration format](https://www.digi.com/resources/documentation/digidocs/90001437-13/reference/r_iso_8601_duration_format.htm)

#### -k=\<key format>

Key format. Default: HEX.

#### -v=\<value format>

Value format. Default: HEX.

#### --key-schema=\<key schema>

Key schema. Default: SCHEMA_REGISTRY.

#### --value-schema=\<value schema>

Value schema. Default: SCHEMA_REGISTRY.

#### -n=<message count>

Message count limit. Default: 9223372036854775807.

## topics (t)