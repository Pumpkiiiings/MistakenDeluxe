plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly(project(":MistakenDeluxe-API"))
    compileOnly(project(":MistakenDeluxe-Core"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    
    // PacketEvents for Camera and Display Entities
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.shadowJar {
    archiveFileName.set("MistakenDeluxe-Preview-Addon-\${project.version}.jar")
}
