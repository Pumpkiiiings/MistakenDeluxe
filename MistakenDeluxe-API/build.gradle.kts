plugins {
    java
    kotlin("jvm") version "2.3.0"
    id("maven-publish")
}

group = "liric.mistaken"
version = "2.1.5-fix"

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
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly(kotlin("stdlib"))

    compileOnly("net.kyori:adventure-text-minimessage:4.26.1")
    compileOnly("org.jetbrains:annotations:26.1.0")
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
}
