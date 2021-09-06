import java.time.LocalDate

plugins {
  java
  scala
  application
  id("com.github.blueboxware.tocme").version("1.3")
}

group = "org.dauch"
version = "0.3.3"

val javaVersion = JavaVersion.VERSION_11
val kafkaVersion = "2.8.0"
val slf4jVersion = "1.7.32"

repositories {
  mavenCentral()
}

tasks.withType<Test> {

  maxParallelForks = 1

  jvmArgs("-Xmx8g")

  systemProperty("java.util.logging.manager", "org.dauch.kc.logging.TestLogManager")
  systemProperty("java.util.logging.config.class", "org.dauch.kc.logging.TestLoggingConfigurer")

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
  implementation(group = "org.apache.kafka", name = "kafka-clients", version = kafkaVersion)
  implementation(group = "org.codehaus.groovy", name = "groovy-json", version = "3.0.8", classifier = "indy")
  implementation(group = "org.apache.avro", name = "avro", version = "1.9.2")
  implementation(group = "org.apache.karaf.shell", name = "org.apache.karaf.shell.table", version = "4.0.10")
  implementation(group = "org.slf4j", name = "slf4j-jdk14", version = slf4jVersion)

  testImplementation(platform("org.junit:junit-bom:5.7.2"))
  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-engine")
  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params")
  testImplementation(group = "org.apache.kafka", name = "kafka_2.13", version = kafkaVersion)
  testImplementation(group = "org.apache.zookeeper", name = "zookeeper", version = "3.6.3") {
    exclude(group = "log4j", module = "log4j")
    exclude(group = "org.slf4j", module = "slf4j-log4j12")
  }
  testImplementation(group = "org.slf4j", name = "log4j-over-slf4j", version = slf4jVersion)
  testImplementation(group = "org.scala-lang", name = "scala-reflect", version = "2.13.6")
  testImplementation(group = "io.dropwizard.metrics", name = "metrics-core", version = "3.2.6")
}

configure<JavaPluginExtension> {
  sourceCompatibility = javaVersion
  targetCompatibility = javaVersion
}

tasks.withType<ScalaCompile> {
  scalaCompileOptions.apply {
    isForce = true
    isDeprecation = true
    additionalParameters = listOf(
      "-release", javaVersion.toString(),
      "-Xfatal-warnings",
      "-Xsource:3"
    )
  }
  targetCompatibility = javaVersion.toString()
}

application {
  mainClass.set("org.dauch.kc.Kc")
  applicationName = "kc"
}

tasks.withType<CreateStartScripts> {
  classpath = files("*", "conf")
}

tasks.named("distTar") {
  setProperty("archiveFileName", "${project.name}.tar")
}

tasks.named<ProcessResources>("processResources") {
  filesMatching("**/*.properties") {
    filter(
      org.apache.tools.ant.filters.ReplaceTokens::class,
      "tokens" to mapOf(
        "project.version" to project.version,
        "year" to LocalDate.now().year.toString()
      )
    )
  }
}

tocme {
  doc("README.md") {
    levels = levels("1-5")
  }
}