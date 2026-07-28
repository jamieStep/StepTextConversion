plugins {
  kotlin("jvm") version "2.3.21"
  application
  id("com.gradleup.shadow") version "8.3.5" // check for latest version
}

application {
  mainClass.set("org.stepbible.helperforadditionallookups.MainKt")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  testImplementation(kotlin("test"))
}

kotlin {
  jvmToolchain(25)
}

tasks.test {
  useJUnitPlatform()
}

