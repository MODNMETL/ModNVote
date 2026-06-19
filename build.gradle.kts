plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
}

group = "com.modnmetl"
version = "2.2.0"
description = "ModNVote 2.0 - secure poll and election platform for Paper"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.5")

    // Gson is provided by the Paper server runtime, so it is compileOnly here and
    // is NOT shaded into the plugin jar. It is added on the test classpath so the
    // election-definition parser can be unit-tested without a running server.
    compileOnly("com.google.code.gson:gson:2.10.1")

    implementation("org.xerial:sqlite-jdbc:3.46.0.0")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.code.gson:gson:2.10.1")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("modnvote-${project.version}-plain.jar")
    manifest {
        attributes(
            "Implementation-Title" to "ModNVote",
            "Implementation-Version" to project.version,
            "Built-By" to "MODN METL"
        )
    }
}

tasks.shadowJar {
    archiveFileName.set("modnvote-${project.version}.jar")
    manifest {
        attributes(
            "Implementation-Title" to "ModNVote",
            "Implementation-Version" to project.version,
            "Built-By" to "MODN METL"
        )
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}
