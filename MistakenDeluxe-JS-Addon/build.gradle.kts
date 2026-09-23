import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    kotlin("jvm")
    id("io.github.goooler.shadow")
}

group = "liric.mistaken.jsaddon"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    
    implementation(project(":MistakenDeluxe-API"))
    compileOnly(project(":MistakenDeluxe-Core"))
    compileOnly("org.luaj:luaj-jse:3.0.1")

    implementation("org.graalvm.js:js:22.3.2")
    implementation("org.graalvm.js:js-scriptengine:22.3.5")
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
