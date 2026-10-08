pluginManagement {
    plugins {
        kotlin("jvm") version "2.0.21"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "openmmo"
include("db")
include("patcher")
include("server")
include("server.login")
include("server.game")
include("server.chat")
