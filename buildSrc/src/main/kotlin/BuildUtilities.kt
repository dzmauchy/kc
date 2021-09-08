const val scalaVersion = "2.13.6"
const val kafkaVersion = "2.8.0"
const val slf4jVersion = "1.7.32"
const val groovyVersion = "3.0.8"
const val scalatestVersion = "3.2.9"

val scalaBinaryVersion: String get() = scalaVersion.split('.').subList(0, 2).joinToString(".")

fun scalaDependency(group: String, name: String, version: String): String = "$group:${name}_$scalaBinaryVersion:$version"
fun scalatestDependency(name: String): String = scalaDependency("org.scalatest", name, scalatestVersion)
