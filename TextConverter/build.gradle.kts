import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.base.DokkaBaseConfiguration


/******************************************************************************/
// DON'T FORGET TO CHANGE ME !!!

version = "4.1.0"


/******************************************************************************/
plugins {
    //id("com.github.johnrengelman.shadow") version "8.1.1"  // Add Shadow plugin.
    //kotlin("jvm") version "1.9.0"
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.dokka") version "1.9.20"
    application
}


/******************************************************************************/
repositories {
    mavenCentral()
}


/******************************************************************************/
group = "org.stepbible.textconverter"


/******************************************************************************/
/* I needed the reflections library -- note 1.  I can't recall whether, in
   addition to mentioning it here, I also had to add it manually to the list in
   File / ProjectStructure / Artifacts.

   Notes 2 and 3:

   It then turned out that reflections in turn needed both javassist and
   kotlin-reflect.  In addition to adding these here, these were ones I
   definitely did have to add to File / ProjectStructure / Artifacts.  I
   did this by following an existing entry to see what the path looked like.
   I then used FileExplorer to locate the things I needed to add, and
   added them using '+' and ExtractedDirectory.
 */

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.google.code.gson:gson:2.10")
    implementation("commons-cli:commons-cli:1.5.0") // Don't upgrade this -- later versions seem to have a bug.
    implementation("commons-codec:commons-codec:1.17.0")
    implementation("commons-io:commons-io:2.16.1")
    implementation("net.sf.saxon:Saxon-HE:12.5")
    implementation("org.jasypt:jasypt:1.9.3")
    implementation("org.reflections:reflections:0.10.2") // Note 1.
    implementation("org.javassist:javassist:3.30.2-GA")  // Note 2.
    implementation(kotlin("reflect"))                    // Note 3.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}


/******************************************************************************/
tasks.test {
    useJUnitPlatform()
}


/******************************************************************************/
kotlin {
    jvmToolchain(8)
}


/******************************************************************************/
application {
    mainClass.set("MainKt")
}


/******************************************************************************/
/* The excludes below are important: files named *.RSA, *.SF and *.DSA have
   something to do with signing, and if present in the JAR prevent it from
   being runnable. */

tasks.jar {
        excludes.addAll(listOf("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA"))
        archiveBaseName.set("TextConverter")            // Base name of your JAR
        archiveVersion.set(project.version.toString())  // Use the project version

        manifest {
            attributes(
                "Main-Class" to "org.stepbible.textconverter.MainKt",
                "Implementation-Version" to project.version,
                "Latest-Update-Reason" to "Revised support for Greek Esther; support for revised reversification data; consolidated book lists."
            )
        }

    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile))
    }

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}



/*----------------------------------------------------------------------------*/
/* Dokka. */

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:dokka-base:1.9.20")
    }
}


tasks.dokkaHtml {
    outputDirectory.set(buildDir.resolve("dokka"))

    dokkaSourceSets {
        configureEach {
            //includes.from("docs/README.md")
            includeNonPublic.set(false)
            skipDeprecated.set(true)
        }
    }
}

tasks.withType<DokkaTask>().configureEach {
    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        customStyleSheets = listOf(buildDir.resolve("jamieDokkaStyles.css"))
    }
}
