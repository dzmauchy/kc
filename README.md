# Collection of Kafka Console Tools (kc)
----------------------------------------

# Table of Contents

<!-- toc -->
- __[Kafka Read-Only Console Tool (kcr)](#kafka-read-only-console-tool-kcr)__
- __[Downloads](#downloads)__
- __[Documentation](#documentation)__
<!-- /toc -->

# Kafka Read-Only Console Tool (kcr)

Kafka Console Read-Only Tool is a command-line utility to make some **read-only** operations:
* to fetch messages from Kafka topics within a given time window filtered by Groovy expressions. Output messages could be transformed using Groovy expression too
* to select messages between offsets
* to get current offset information (including starting date of the first offset)
* to get topic listing
* to get consumer groups
* to fetch a schema from Schema Registry based on schema-id field in a message taken by the specified offset

**All operations are read-only** so you <u>can't ruin your career by using this utility</u>.

See the [Detailed Documentation](kc-ro.md).

# Downloads

All files could be downloaded from [Releases](https://github.com/dzmauchy/kc/releases)

# Documentation

* [Kafka Read-Only Console Tool](kc-ro.md)