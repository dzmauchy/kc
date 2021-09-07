plugins {
  id("com.github.blueboxware.tocme").version("1.3")
  id("org.cadixdev.licenser").version("0.6.1")
}

group = "org.dauch"
version = "0.3.3"

val javaVersion = JavaVersion.VERSION_11
val scalaVersion: String by project
val kafkaVersion: String by project
val slf4jVersion: String by project

subprojects {
  repositories {
    mavenCentral()
  }

  apply(plugin = "java")
  apply(plugin = "scala")
  apply(plugin = "org.cadixdev.licenser")

  tasks.withType<Test> {

    maxParallelForks = 1

    jvmArgs("-Xmx8g")

    systemProperty("java.util.logging.manager", "org.dauch.test.logging.TestLogManager")
    systemProperty("java.util.logging.config.class", "org.dauch.test.logging.TestLoggingConfigurer")

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

  configure<JavaPluginExtension> {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
  }

  tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf(
      "-parameters",
      "-Xlint:deprecation"
    ))
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

  dependencies {
    /* IMPORTANT! uncomment the below line to allow IDEA to import the project without errors */
    //"compileOnly"(group = "org.scala-lang", name = "scala-reflect", version = scalaVersion)
  }

  when (name) {
    "kc-test", "kc-core", "kcr" -> {
      apply(plugin = "java-library")
    }
  }

  when (name) {
    "kc-core" -> {
      dependencies {
        "api"(group = "org.apache.kafka", name = "kafka-clients", version = kafkaVersion)
        "api"(group = "org.apache.avro", name = "avro", version = "1.10.2")
        "api"(group = "org.slf4j", name = "slf4j-jdk14", version = slf4jVersion)
      }
    }
    "kc-test" -> {
      dependencies {
        "api"(platform("org.junit:junit-bom:5.7.2"))
        "api"(group = "org.junit.jupiter", name = "junit-jupiter-engine")
        "api"(group = "org.junit.jupiter", name = "junit-jupiter-params")
        "api"(group = "org.apache.kafka", name = "kafka_2.13", version = kafkaVersion)
        "api"(group = "org.apache.zookeeper", name = "zookeeper", version = "3.6.3") {
          exclude(group = "log4j", module = "log4j")
          exclude(group = "org.slf4j", module = "slf4j-log4j12")
        }
        "api"(group = "org.slf4j", name = "log4j-over-slf4j", version = slf4jVersion)
        "api"(group = "org.scala-lang", name = "scala-reflect", version = "2.13.6")
        "api"(group = "io.dropwizard.metrics", name = "metrics-core", version = "3.2.6")
      }
    }
    "kcr" -> {
      dependencies {
        "api"(project(":kc-core"))
        "api"(group = "org.codehaus.groovy", name = "groovy-json", version = "3.0.8", classifier = "indy")
        "api"(group = "org.apache.karaf.shell", name = "org.apache.karaf.shell.table", version = "4.0.10")
        "testImplementation"(project(":kc-test"))
      }
    }
  }

  when (name) {
    "kcr" -> {
      apply(plugin = "application")

      tasks.named<ProcessResources>("processResources") {
        filesMatching("**/*.properties") {
          filter(
            org.apache.tools.ant.filters.ReplaceTokens::class,
            "tokens" to mapOf(
              "project.version" to project.version,
              "year" to java.time.LocalDate.now().year.toString()
            )
          )
        }
      }

      tasks.withType<CreateStartScripts> {
        classpath = files("*", "conf")
      }

      configure<JavaApplication> {
        mainClass.set("org.dauch.kc.Kcr")
        applicationName = "kcr"
      }
    }
  }

  afterEvaluate {
    tasks.getByName("clean") {
      dependsOn(
        tasks.getByName("updateLicenses")
      )
    }
  }

  license {
    include("**/*.java")
    include("**/*.scala")
    setHeader(rootProject.file("license_header.txt"))
    newLine(false)
  }
}

tocme {
  doc("README.md") {
    levels = levels("1-5")
  }
}