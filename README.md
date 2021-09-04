Kafka Console (kc)
------------------

# Table of Contents

<!-- toc -->
- __[Summary](#summary)__
- __[Downloads](#downloads)__
- __[Distribution](#distribution)__
- __[Requirements](#requirements)__
- __[Commands](#commands)__
  - __[Common parameters](#common-parameters)__
    - __[For all commands](#for-all-commands)__
      - __[--quiet Quiet output flag](#--quiet-quiet-output-flag)__
    - __[For all commands accessing Kafka](#for-all-commands-accessing-kafka)__
      - __[--bootstrap-servers= Kafka bootstrap servers](#--bootstrap-servers-kafka-bootstrap-servers)__
    - __[For all commands reading Kafka topics](#for-all-commands-reading-kafka-topics)__
      - __[--schema-registry= Schema Registry URL](#--schema-registry-schema-registry-url)__
    - __[For all commands fetching data](#for-all-commands-fetching-data)__
      - __[-f=, --filter= Filter expression](#-f---filter-filter-expression)__
      - __[-p=, --projection= Projection expression](#-p---projection-projection-expression)__
      - __[-t= Kafka poll timeout.](#-t-kafka-poll-timeout)__
      - __[-k= Key format](#-k-key-format)__
      - __[-v= Value format](#-v-value-format)__
      - __[--key-schema= Key schema (-k=AVRO only)](#--key-schema-key-schema--kavro-only)__
      - __[--value-schema= Value schema (-v=AVRO only)](#--value-schema-value-schema--vavro-only)__
      - __[-n= Message count limit](#-n-message-count-limit)__
    - __[For all commands dealing with Kafka Admin Client](#for-all-commands-dealing-with-kafka-admin-client)__
      - __[--client-properties= Client properties](#--client-properties-client-properties)__
      - __[--timeout= Client query timeout](#--timeout-client-query-timeout)__
      - __[-p Pretty print flag](#-p-pretty-print-flag)__
  - __[Topics (t)](#topics-t)__
    - __[Parameters](#parameters)__
    - __[--list-internal Include internal topics flag](#--list-internal-include-internal-topics-flag)__
  - __[Fetch (f)](#fetch-f)__
  - __[Select (s)](#select-s)__
  - __[Consumer groups (g)](#consumer-groups-g)__
  - __[Fetch AVRO schema (S)](#fetch-avro-schema-s)__
- __[Formats (to use with -k and -v options)](#formats-to-use-with--k-and--v-options)__
- __[Time instants](#time-instants)__
<!-- /toc -->

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

#### --bootstrap-servers= Kafka bootstrap servers
Comma-separated list of the Kafka bootstrap servers.
|Default|Value|
|-|-|
|1st default|KAFKA_BOOTSTRAP_SERVERS environment variable|
|2nd default|localhost:9092|

### For all commands reading Kafka topics

#### --schema-registry= Schema Registry URL
AVRO schema registry url with slash at the end, e.g. ```http://my-server.com/```
|Default|Value|
|-|-|
|1st default|SCHEMA_REGISTRY environment variable|
|2nd default|http://localhost:2181/|

### For all commands fetching data

#### -f=, --filter= Filter expression

A groovy expression to filter incoming records.

|Default|Value|
|-|-|
|1st default|```true```|

#### -p=, --projection= Projection expression

A groovy expression to map the filtered records to an appropriate structure.

|Default|Value|
|-|-|
|1st default|```[t: $r.topic(), p: $r.partition(), o: $r.offset(), k: $k, v: $v]```|

#### -t= Kafka poll timeout.

Poll timeout.
Default value is PT5S (5s).

See [ISO duration format](https://www.digi.com/resources/documentation/digidocs/90001437-13/reference/r_iso_8601_duration_format.htm)

#### -k= Key format

Key format. Default: HEX.

#### -v= Value format

Value format. Default: HEX.

#### --key-schema= Key schema (-k=AVRO only)

Key schema. Default: SCHEMA_REGISTRY.

#### --value-schema= Value schema (-v=AVRO only)

Value schema. Default: SCHEMA_REGISTRY.

#### -n= Message count limit

Message count limit. Default: 9223372036854775807.

### For all commands dealing with Kafka Admin Client

#### --client-properties= Client properties

All properties are defined as a list of key-value pairs separated by ,, (two commas).

#### --timeout= Client query timeout

Client query timeout. Default: PT5M.

See [ISO duration format](https://www.digi.com/resources/documentation/digidocs/90001437-13/reference/r_iso_8601_duration_format.htm)

#### -p Pretty print flag

If specified, prettifies the output JSON.

## Topics (t)

### Parameters

Topic patterns (regex expressions: see [Regexp](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/regex/Pattern.html))

### --list-internal Include internal topics flag

If specified, internal topics will be included too.

## Fetch (f)

## Select (s)

## Consumer groups (g)

## Fetch AVRO schema (S)

# Formats (to use with -k and -v options)

Name | Description |
-----| ------------|
HEX  | Hex format: (\<count> \<hex dump>)
BASE64| Base64 dump
BYTES| Raw bytes
STRING| UTF-8 encoded string
JSON| JSON document
LONG| 64-bit integer
INT|32-bit integer
FLOAT|32-bit floating point number
DOUBLE|64-bit floating point number
AVRO|AVRO-encoded message

# Time instants

Example | Description
--------|------------
now | Current timestamp
today | 00:00:00 of current day
yesterday | 00:00:00 of yesterday
tomorrow | 00:00:00 of tomorrow
start | 1970-01-01T00:00:00
epoch | 1970-01-01T00:00:00
2020-08-03T | 2020-08-03T00:00:00
2020-08T | 2020-08-01T00:00:00
2020T | 2020-01-01T00:00:00
2020-08-03T09:33:21 | 2020-08-03T09:33:21
2020-08-03T09:33 | 2020-08-03T09:33:00
2020-08-03T09 | 2020-08-03T09:00:00