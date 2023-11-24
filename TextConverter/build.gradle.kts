plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "org.stepbible.textconverter"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("commons-cli:commons-cli:1.5.0")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("commons-io:commons-io:2.13.0")
    implementation("net.sf.saxon:Saxon-HE:12.3")
    implementation("org.jasypt:jasypt:1.9.3")
    implementation(kotlin("reflect"))
    implementation("io.arrow-kt:arrow-core:1.2.0")
    implementation("io.arrow-kt:arrow-fx-coroutines:1.2.0")
}


tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("MainKt")
}