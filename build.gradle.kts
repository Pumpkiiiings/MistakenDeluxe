import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget // 🔥 IMPORTANTE: Necesario para el nuevo compilerOptions

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.ow2.asm:asm:9.6")
        classpath("org.ow2.asm:asm-commons:9.6")
    }
}

plugins {
    java
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.3.0"
    id("maven-publish")
}

group = "liric.mistaken" // Actualizado a tu nuevo package
version = "2.1.5-fix"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.triumphteam.dev/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.momirealms.net/releases/")
    maven("https://repo.helpch.at/releases")
    maven("https://repo.infernalsuite.com/repository/maven-snapshots/")
    maven("https://maven.blamejared.com/")
    maven("https://maven.nucleoid.xyz/")

    flatDir {
        dirs("libs")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib"))
    implementation("com.mojang:brigadier:1.2.9")
    // Librerías que se incluirán en el JAR (Shadow)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.github.retrooper:packetevents-spigot:2.12.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("fr.skytasul:glowingentities:1.4.10")
    implementation("com.mysql:mysql-connector-j:8.3.0")
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("org.xerial:sqlite-jdbc:3.45.2.0")
    implementation("dev.triumphteam:triumph-gui:3.1.13")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("com.infernalsuite.asp:file-loader:4.0.0-SNAPSHOT")

    // APIs Externas (Solo para compilar)
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:2.0.0")
    compileOnly("com.infernalsuite.asp:api:4.0.0-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("net.momirealms:craft-engine-core:0.0.67")
    compileOnly("net.momirealms:craft-engine-bukkit:0.0.67")
    compileOnly(files("libs/CraftEngine.jar"))
    compileOnly("net.luckperms:api:5.4")
    compileOnly("me.clip:placeholderapi:2.11.7")

    // Paper ya incluye Adventure y MiniMessage nativamente
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        isZip64 = true
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        // RELOCACIONES: Para que no haya conflicto con otros plugins
        relocate("com.github.retrooper.packetevents", "liric.mistaken.libs.packetevents")
        relocate("io.github.retrooper.packetevents", "liric.mistaken.libs.packetevents")
        relocate("dev.triumphteam.gui", "liric.mistaken.libs.gui")
        relocate("fr.skytasul.glowingentities", "liric.mistaken.libs.glowing")

        relocate("com.zaxxer.hikari", "liric.mistaken.libs.hikari")
        relocate("org.slf4j", "liric.mistaken.libs.slf4j")
        relocate("kotlin", "liric.mistaken.libs.kotlin")
        relocate("kotlinx.coroutines", "liric.mistaken.libs.coroutines")
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    withType<KotlinCompile> {
        // 🔥 CORRECCIÓN: Migración de kotlinOptions a compilerOptions
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
