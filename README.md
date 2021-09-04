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
      - __[--bootstrap-servers=<Kafka bootstrap server list> Kafka bootstrap servers](#--bootstrap-serverskafka-bootstrap-server-list-kafka-bootstrap-servers)__
    - __[For all commands reading Kafka topics](#for-all-commands-reading-kafka-topics)__
      - __[--schema-registry=<AVRO schema registry url> Schema Registry URL](#--schema-registryavro-schema-registry-url-schema-registry-url)__
    - __[For all commands fetching data](#for-all-commands-fetching-data)__
      - __[-f=<filter expression>, --filter=<filter expression> Filter expression](#-ffilter-expression---filterfilter-expression-filter-expression)__
      - __[-p=<projection expression>, --projection=<projection expression> Projection expression](#-pprojection-expression---projectionprojection-expression-projection-expression)__
      - __[-t=<poll timeout> Kafka poll timeout.](#-tpoll-timeout-kafka-poll-timeout)__
      - __[-k=<key format> Key format](#-kkey-format-key-format)__
      - __[-v=<value format> Value format](#-vvalue-format-value-format)__
      - __[--key-schema=<key schema> (-k=AVRO only)](#--key-schemakey-schema--kavro-only)__
      - __[--value-schema=<value schema> Value schema (-v=AVRO only)](#--value-schemavalue-schema-value-schema--vavro-only)__
      - __[-n=<message count> Message count limit](#-nmessage-count-message-count-limit)__
    - __[For all commands dealing with Kafka Admin Client.](#for-all-commands-dealing-with-kafka-admin-client)__
      - __[--client-properties=<client properties> Client properties](#--client-propertiesclient-properties-client-properties)__
      - __[--timeout=<timeout> Client query timeout](#--timeouttimeout-client-query-timeout)__
      - __[-p Pretty print flag](#-p-pretty-print-flag)__
  - __[topics (t)](#topics-t)__
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