plugins {
  java
  id("com.github.breadmoirai.github-release").version("2.2.12")
}

group = "org.ku"
version = "0.1-SNAPSHOT"

val javaVersion = JavaVersion.VERSION_11

repositories {
  mavenCentral()
  maven("https://plugins.gradle.org/m2/")
}

dependencies {
  testCompile("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
  sourceCompatibility = javaVersion
  targetCompatibility = javaVersion
}

githubRelease {
  owner("ku")
  repo("kc")
  tagName(project.version.toString())
  body("Kafka Command Line ${project.version}")
}