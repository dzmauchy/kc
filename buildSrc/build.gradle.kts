plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  implementation(kotlin("reflect"))

  testImplementation(kotlin("test-junit5"))
}