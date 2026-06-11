import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    kotlin("jvm")
    id("com.github.johnrengelman.shadow")
    id("io.github.revxrsal.zapper")
}

group = "liric.mistaken.level"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://nexus.frengor.com/repository/public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

zapper {
    libsFolder = "libraries-addon-level"
    relocationPrefix = "liric.mistaken.libs"

    repositories {
        includeProjectRepositories()
    }

    relocate("dev.triumphteam.gui", "gui")
    relocate("com.zaxxer.hikari", "hikari")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("io.github.revxrsal:zapper.api:1.0.3")
    compileOnly("me.clip:placeholderapi:2.11.5")
    
    // Core API
    implementation(project(":MistakenDeluxe-API"))
    // Required to reuse pumpking.lib database infrastructure at compile time
    compileOnly(project(":MistakenDeluxe-Core"))

    // Addon specific Zapper dependencies
    zap("com.zaxxer:HikariCP:5.1.0")
    zap("com.mysql:mysql-connector-j:8.4.0")
    zap("org.xerial:sqlite-jdbc:3.53.2.0")
    zap("dev.triumphteam:triumph-gui:3.1.13")

    // compileOnly("com.frengor:ultimateadvancementapi:2.8.0")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        isZip64 = true
    }

    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    build {
        dependsOn(shadowJar)
    }
}
