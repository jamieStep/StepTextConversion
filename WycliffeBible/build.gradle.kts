plugins {
    kotlin("jvm") version "2.0.20"
}

group = "org.stepbible.wycliffeBible"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.sf.saxon:Saxon-HE:12.5")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(18)
}