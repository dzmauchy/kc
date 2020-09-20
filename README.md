Kafka Console
-------------
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

## topics (t)

