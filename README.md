Kafka Console (kc)
------------------

- [Summary](#summary)
- [Downloads](#downloads)
- [Distribution](#distribution)
- [Requirements](#requirements)
- [Commands](#commands)
  * [Common parameters](#common-parameters)
    + [For all commands](#for-all-commands)
      - [--quiet Quiet output flag](#--quiet-quiet-output-flag)
    + [For all commands accessing Kafka](#for-all-commands-accessing-kafka)
      - [--bootstrap-servers=\<Kafka bootstrap server list> Kafka bootstrap servers](#--bootstrap-servers---kafka-bootstrap-server-list--kafka-bootstrap-servers)
    + [For all commands reading Kafka topics](#for-all-commands-reading-kafka-topics)
      - [--schema-registry=\<AVRO schema registry url> Schema Registry URL](#--schema-registry---avro-schema-registry-url--schema-registry-url)
    + [For all commands fetching data](#for-all-commands-fetching-data)
      - [-f=\<filter expression>, --filter=\<filter expression> Filter expression](#-f---filter-expression-----filter---filter-expression--filter-expression)
      - [-p=\<projection expression>, --projection=\<projection expression> Projection expression](#-p---projection-expression-----projection---projection-expression--projection-expression)
      - [-t=\<poll timeout> Kafka poll timeout.](#-t---poll-timeout--kafka-poll-timeout)
      - [-k=\<key format> Key format](#-k---key-format--key-format)
      - [-v=\<value format> Value format](#-v---value-format--value-format)
      - [--key-schema=\<key schema> (-k=AVRO only)](#--key-schema---key-schema----k-avro-only-)
      - [--value-schema=\<value schema> Value schema (-v=AVRO only)](#--value-schema---value-schema--value-schema---v-avro-only-)
      - [-n=\<message count> Message count limit](#-n---message-count--message-count-limit)
    + [For all commands dealing with Kafka Admin Client.](#for-all-commands-dealing-with-kafka-admin-client)
      - [--client-properties=\<client properties> Client properties](#--client-properties---client-properties--client-properties)
      - [--timeout=\<timeout> Client query timeout](#--timeout---timeout--client-query-timeout)
      - [-p Pretty print flag](#-p-pretty-print-flag)
  * [topics (t)](#topics--t-)

<small><i><a href='http://ecotrust-canada.github.io/markdown-toc/'>Table of contents generated with markdown-toc</a></i></small>

# Summary

Kafka Console is a command-line utility to make some **read-only** operations:
* to fetch messages from Kafka topics within a given time window filtered by Groovy expressions. Output messages could be transformed using Groovy expression too
* to select messages between offsets
* to get current offset information (including starting date of the first offset)
* to get topic listing
* to get consumer groups
* to fetch a schema from Schema Registry based on schema-id field in a message taken by the specified offset

**All operations are read-only** so you <u>can't ruin your career by using this utility</u>.

# Downloads

All files could be downloaded from [Releases](https://github.com/dzmauchy/kc/releases)

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

#### --quiet Quiet output flag

If specified, disables additional output to stderr.

### For all commands accessing Kafka

#### --bootstrap-servers=\<Kafka bootstrap server list> Kafka bootstrap servers
Comma-separated list of the Kafka bootstrap servers.
|Default|Value|
|-|-|
|1st default|KAFKA_BOOTSTRAP_SERVERS environment variable|
|2nd default|localhost:9092|

### For all commands reading Kafka topics

#### --schema-registry=\<AVRO schema registry url> Schema Registry URL
AVRO schema registry url with slash at the end, e.g. ```http://my-server.com/```
|Default|Value|
|-|-|
|1st default|SCHEMA_REGISTRY environment variable|
|2nd default|http://localhost:2181/|

### For all commands fetching data

#### -f=\<filter expression>, --filter=\<filter expression> Filter expression

A groovy expression to filter incoming records.

|Default|Value|
|-|-|
|1st default|```true```|

#### -p=\<projection expression>, --projection=\<projection expression> Projection expression

A groovy expression to map the filtered records to an appropriate structure.

|Default|Value|
|-|-|
|1st default|```[t: $r.topic(), p: $r.partition(), o: $r.offset(), k: $k, v: $v]```|

#### -t=\<poll timeout> Kafka poll timeout.

Poll timeout.
Default value is PT5S (5s).

See [ISO duration format](https://www.digi.com/resources/documentation/digidocs/90001437-13/reference/r_iso_8601_duration_format.htm)

#### -k=\<key format> Key format

Key format. Default: HEX.

#### -v=\<value format> Value format

Value format. Default: HEX.

#### --key-schema=\<key schema> (-k=AVRO only)

Key schema. Default: SCHEMA_REGISTRY.

#### --value-schema=\<value schema> Value schema (-v=AVRO only)

Value schema. Default: SCHEMA_REGISTRY.

#### -n=\<message count> Message count limit

Message count limit. Default: 9223372036854775807.

### For all commands dealing with Kafka Admin Client.

#### --client-properties=\<client properties> Client properties

All properties are defined as a list of key-value pairs separated by ,, (two commas).

#### --timeout=\<timeout> Client query timeout

Client query timeout. Default: PT5M.

See [ISO duration format](https://www.digi.com/resources/documentation/digidocs/90001437-13/reference/r_iso_8601_duration_format.htm)

#### -p Pretty print flag

If specified, prettifies the output JSON.

## topics (t)