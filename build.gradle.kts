plugins {
    kotlin("jvm") version "2.4.20-Beta1"
    id("com.gradleup.shadow") version "9.4.3"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

val kotlinVersion = "2.4.20-Beta1"
val hikariVersion = "6.3.0"
val mongoVersion = "5.5.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://jitpack.io")
}

dependencies {
    implementation(project(":api"))
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("com.zaxxer:HikariCP:$hikariVersion")
    compileOnly("org.mongodb:mongodb-driver-sync:$mongoVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        minimize {
            exclude(project(":api"))
        }
        dependencies {
            exclude(dependency("org.jetbrains.kotlin:.*:.*"))
            exclude(dependency("org.jetbrains:annotations:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.21.8-R0.1-SNAPSHOT")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf(
            "version" to version,
            "kotlinVersion" to kotlinVersion,
            "hikariVersion" to hikariVersion,
            "mongoVersion" to mongoVersion,
        )
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
