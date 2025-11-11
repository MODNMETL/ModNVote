import org.gradle.jvm.tasks.Jar

plugins { `java` }

group = "com.modnmetl"
version = "1.1.2"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("org.xerial:sqlite-jdbc:3.46.0.1")
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}

/** Fat/uber jar (no relocation). Output: build/libs/modnvote-1.1.0.jar */
val uberJar = tasks.register<Jar>("uberJar") {
    group = "build"
    description = "Builds a fat jar including runtime dependencies (no relocation)."
    archiveClassifier.set("")

    from(sourceSets.main.get().output)

    val runtimeCp = configurations.runtimeClasspath.get()
    from(
        runtimeCp
            .filter { it.exists() }
            .map { if (it.isDirectory) it else zipTree(it) }
    )

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Implementation-Title" to "ModNVote",
            "Implementation-Version" to project.version
        )
    }
}

tasks.build { dependsOn(uberJar) }
