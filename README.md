Kafka Console (kc)
------------------
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


## topics (t)

