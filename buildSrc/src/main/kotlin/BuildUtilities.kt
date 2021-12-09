const val scalaVersion = "2.13.7"
const val kafkaVersion = "2.8.1"
const val slf4jVersion = "1.7.32"
const val groovyVersion = "3.0.9"
const val scalatestVersion = "3.2.10"
const val avroVersion = "1.10.2"

val scalaBinaryVersion: String get() = scalaVersion.split('.').subList(0, 2).joinToString(".")

fun scalaDependency(group: String, name: String, version: String): String = "$group:${name}_$scalaBinaryVersion:$version"
fun scalatestDependency(name: String): String = scalaDependency("org.scalatest", name, scalatestVersion)
