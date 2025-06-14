plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.0"
}

group = "ca.atlasengine.scripting"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.minestom:minestom-snapshots:1_21_5-c4814c2270")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation("org.graalvm.sdk:graal-sdk:24.0.0") // Provides org.graalvm.polyglot.*
    implementation("org.graalvm.js:js:24.2.1")         // Provides the JavaScript engine
    implementation("org.graalvm.js:js-scriptengine:24.0.0") // Provides JSR 223 script engine wrapper

    // SLF4J API
    implementation("org.slf4j:slf4j-api:2.0.13")
    // Simple logger binding
    implementation("org.slf4j:slf4j-simple:2.0.13")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21)) // Minestom has a minimum Java version of 21
    }
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "ca.atlasengine.scripting.Main" // Change this to your main class
        }
    }

    build {
        dependsOn(shadowJar)
    }
    shadowJar {
        mergeServiceFiles()
        archiveClassifier.set("") // Prevent the -all suffix on the shadowjar file.
    }
}