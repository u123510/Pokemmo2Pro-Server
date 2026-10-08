plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.7")
}

tasks.shadowJar {
    archiveBaseName.set("patcher")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes(
            "Premain-Class" to "org.patcher.PatcherAgent"
        )
    }
}
