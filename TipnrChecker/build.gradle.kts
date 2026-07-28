plugins {
  application
    kotlin("jvm") version "2.3.20"
}

group = "org.stepbible.tipnrchecker"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
  implementation("net.sf.saxon:Saxon-HE:12.5")
  testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(18)
}

tasks.test {
    useJUnitPlatform()
}

application {
  mainClass.set("org.stepbible.tipnrchecker.MainKt")
}

tasks.named<JavaExec>("run") {
  isIgnoreExitValue = true
  standardInput = System.`in`
  standardOutput = System.out
  errorOutput = System.err
}