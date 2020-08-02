plugins {
  java
}

group = "org.ku"
version = "0.1-SNAPSHOT"

val javaVersion = JavaVersion.VERSION_11
val confluentVersion = "5.4.2"

repositories {
  mavenCentral()
  maven("https://plugins.gradle.org/m2/")
  maven("https://packages.confluent.io/maven/")
}

tasks.withType<Test> {

  maxParallelForks = 1

  jvmArgs(
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.management/sun.management=ALL-UNNAMED",
    "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED"
  )

  testLogging {
    events = enumValues<org.gradle.api.tasks.testing.logging.TestLogEvent>().toSet()
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    showExceptions = true
    showCauses = true
    showStackTraces = true
    showStandardStreams = true
    maxGranularity = 3
    minGranularity = 3
  }

  useJUnitPlatform {
    includeTags("normal")
  }
}

configurations.all {
  resolutionStrategy.eachDependency {
    when {
      requested.group == "org.jboss.xnio" -> useVersion("3.8.1.Final")
      requested.group == "org.jboss.threads" && requested.name == "jboss-threads" -> useVersion("3.1.1.Final")
      requested.group == "com.fasterxml.jackson.core" -> useVersion("2.10.5")
    }
  }
}

dependencies {
  implementation(group = "org.apache.kafka", name = "kafka-clients", version = "$confluentVersion-ccs")
  implementation(group = "io.confluent", name = "kafka-schema-registry-client", version = confluentVersion)
  implementation(group = "org.codehaus.groovy", name = "groovy", version = "3.0.5", classifier = "indy")
  implementation(group = "io.undertow", name = "undertow-core", version = "2.1.3.Final")
  implementation(group = "org.slf4j", name = "slf4j-jdk14", version = "1.7.30")
  implementation(group = "org.jboss.logmanager", name = "jboss-logmanager", version = "2.1.17.Final")

  testImplementation(platform("org.junit:junit-bom:5.6.2"))
  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-engine")
  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params")
}

configure<JavaPluginConvention> {
  sourceCompatibility = javaVersion
  targetCompatibility = javaVersion
}