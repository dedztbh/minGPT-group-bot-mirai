import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.0"
    id("com.github.johnrengelman.shadow") version "6.0.0"
}

group = "com.dedztbh"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    val mirai_version = "1.2.1"
    val ktor_version = "1.4.0"
    implementation("net.mamoe:mirai-core-qqandroid:$mirai_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-gson:$ktor_version")
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("mingpt_group_bot_mirai")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "com.dedztbh.mingpt_group_bot_mirai.MainKt"))
        }
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}