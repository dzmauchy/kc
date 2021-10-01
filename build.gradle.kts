plugins {
  id("com.github.blueboxware.tocme").version("1.3")
  id("org.cadixdev.licenser").version("0.6.1")
}

group = "org.dauch"
version = "0.3.6"

val javaVersion = JavaVersion.VERSION_11

val licenseHeaderFile = file("license_header.txt")

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
    options.compilerArgs.addAll(
      listOf(
        "-parameters",
        "-Xlint:deprecation"
      )
    )
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
    "kc-test", "kc-core", "kc-ro" -> {
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
        "api"(group = "org.slf4j", name = "slf4j-jdk14", version = slf4jVersion)
        "api"(group = "org.scala-lang", name = "scala-reflect", version = scalaVersion)
        "api"(group = "io.dropwizard.metrics", name = "metrics-core", version = "3.2.6")
        "api"(scalatestDependency(name = "scalatest"))
        "api"(scalatestDependency(name = "scalatest-shouldmatchers"))
      }
    }
    "kc-ro" -> {
      dependencies {
        "api"(project(":kc-core"))
        "api"(group = "org.codehaus.groovy", name = "groovy", version = groovyVersion, classifier = "indy")
        "api"(group = "org.codehaus.groovy", name = "groovy-json", version = groovyVersion, classifier = "indy") {
          exclude(group = "org.codehaus.groovy", module = "groovy")
        }
        "api"(group = "org.apache.karaf.shell", name = "org.apache.karaf.shell.table", version = "4.0.10")
        "testImplementation"(project(":kc-test"))
      }
    }
    "kc-rw" -> {
      dependencies {
        "implementation"(project(":kc-ro"))
        "testImplementation"(project(":kc-test"))
      }
    }
  }

  when (name) {
    "kc-ro", "kc-rw" -> {
      apply(plugin = "application")

      @Suppress("UnstableApiUsage")
      tasks.named<ProcessResources>("processResources") {
        filesMatching("**/*.properties") {
          filter(
            org.apache.tools.ant.filters.ReplaceTokens::class,
            "tokens" to mapOf(
              "project.version" to rootProject.version,
              "year" to java.time.LocalDate.now().year.toString()
            )
          )
        }
      }

      tasks.withType<CreateStartScripts> {
        classpath = files("*", "conf")
      }
    }
  }

  when (name) {
    "kc-ro" -> {
      configure<JavaApplication> {
        mainClass.set("org.dauch.kc.Kcr")
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
    setHeader(licenseHeaderFile)
    newLine(false)
  }
}

tocme {
  listOf("README.md", "kc-ro.md").forEach { file ->
    doc(file) {
      levels = levels("1-5")
    }
  }
}