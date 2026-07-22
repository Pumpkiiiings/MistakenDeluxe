import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget // ðŸ”¥ IMPORTANTE: Necesario para el nuevo compilerOptions

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.ow2.asm:asm:9.10.1")
    }
}

plugins {
    java
    kotlin("jvm") version "2.3.0"
    id("io.github.goooler.shadow") version "8.1.8"
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
    
    compileOnly(kotlin("stdlib"))
    compileOnly("com.mojang:brigadier:1.2.9")
    // Librerías que se incluirán en el JAR (Shadow)
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("fr.skytasul:glowingentities:1.4.11")
    compileOnly("com.mysql:mysql-connector-j:9.7.0")
    compileOnly("org.postgresql:postgresql:42.7.13")
    compileOnly("org.xerial:sqlite-jdbc:3.53.2.0")
    compileOnly("dev.triumphteam:triumph-gui:3.1.13")
    compileOnly("org.slf4j:slf4j-simple:2.0.18")
    implementation("com.infernalsuite.asp:file-loader:4.0.0-SNAPSHOT")

    // APIs Externas (Solo para compilar)
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:2.0.0")
    compileOnly("com.infernalsuite.asp:api:4.0.0-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("net.momirealms:craft-engine-core:0.0.67.11")
    compileOnly("net.momirealms:craft-engine-bukkit:0.0.67.11")
    compileOnly(files("libs/CraftEngine.jar"))
    compileOnly(files("C:/Users/L900m/OneDrive/Desktop/PkCinematic/build/libs/PkCinematics-1.2-FIX-dev-all.jar"))
    compileOnly("net.luckperms:api:5.5")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly(files("libs/observer-paper.jar"))

    // Paper ya incluye Adventure y MiniMessage nativamente
    compileOnly("net.kyori:adventure-text-minimessage:4.26.1")
    compileOnly("org.jetbrains:annotations:26.0.1")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        isZip64 = true
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    withType<KotlinCompile> {
        // ðŸ”¥ CORRECCIÃ“N: MigraciÃ³n de kotlinOptions a compilerOptions
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
