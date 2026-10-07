plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.drappula:ArcadeAPI:1.0.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

val copyArcadeCore = tasks.register<Copy>("copyArcadeCore") {
    dependsOn(gradle.includedBuild("ArcadeCore").task(":ArcadePlugin:shadowJar"))
    from(File(gradle.includedBuild("ArcadeCore").projectDir, "ArcadePlugin/build/libs/ArcadePlugin-${version}-all.jar"))
    into(layout.projectDirectory.dir("run/plugins"))
}

tasks {
    runServer {
        dependsOn(copyArcadeCore)
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}
