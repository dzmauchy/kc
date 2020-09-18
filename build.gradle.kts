plugins {
  java
  application
}

group = "org.ku"
version = "0.1.1"

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

  systemProperty("java.util.logging.config.class", "org.ku.kc.logging.TestLoggingConfigurer")

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

dependencies {
  implementation(group = "org.apache.kafka", name = "kafka-clients", version = "$confluentVersion-ccs")
  implementation(group = "org.codehaus.groovy", name = "groovy-json", version = "3.0.5", classifier = "indy")
  implementation(group = "org.apache.avro", name = "avro", version = "1.9.2")
  implementation(group = "org.apache.karaf.shell", name = "org.apache.karaf.shell.table", version = "4.0.10")
  implementation(group = "org.slf4j", name = "slf4j-jdk14", version = "1.7.30")

  testImplementation(platform("org.junit:junit-bom:5.6.2"))
  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-engine")
  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params")
}

configure<JavaPluginConvention> {
  sourceCompatibility = javaVersion
  targetCompatibility = javaVersion
}

application {
  mainClassName = "org.ku.kc.Kc"
  applicationName = "kc"
}

tasks.withType<CreateStartScripts> {
  classpath = files("*", "conf")
}