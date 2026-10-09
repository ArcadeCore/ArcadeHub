plugins {
    id("java-library")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("org.jspecify:jspecify:1.0.0")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.drappula:ArcadeAPI:1.0.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

// Production code must load on Java 8 servers (1.8 era).
tasks.named<JavaCompile>("compileJava") {
    options.release.set(8)
}

tasks {
    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
