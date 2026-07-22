import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    kotlin("jvm")
    id("io.github.goooler.shadow")
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



dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    
    // Core API
    implementation(project(":MistakenDeluxe-API"))
    // Required to reuse pumpking.lib database infrastructure at compile time
    compileOnly(project(":MistakenDeluxe-Core"))

    // Addon specific dependencies (provided by bootstrapper)
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("com.mysql:mysql-connector-j:9.7.0")
    compileOnly("org.xerial:sqlite-jdbc:3.53.2.0")
    compileOnly("dev.triumphteam:triumph-gui:3.1.13")

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
