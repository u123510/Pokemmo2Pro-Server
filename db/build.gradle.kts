plugins {
    java
    id("org.jooq.jooq-codegen-gradle") version "3.19.10"
}

group = "org.pokemmo"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    jooqCodegen("org.postgresql:postgresql:42.7.3")
    jooqCodegen("org.jooq:jooq-meta-extensions:3.19.13")
    jooqCodegen("org.jooq:jooq-postgres-extensions:3.19.13")

    implementation("org.jooq:jooq:3.19.13")
    implementation("org.jooq:jooq-codegen:3.19.13")
    implementation("org.jooq:jooq-meta:3.19.13")
    implementation("org.jooq:jooq-postgres-extensions:3.19.13")

    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.postgresql:postgresql:42.7.3")

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
}
jooq {
    configuration {
        val host = "localhost"
        val port = 5432
        val db_user = "openmmo"
        val db_password = "12345678"
        val database = "mmo_db"
        jdbc {
            driver = "org.postgresql.Driver"
            url = "jdbc:postgresql://$host:$port/$database"
            user = db_user
            password = db_password
        }
        generator {
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                includes = ".*"
                excludes = ""
                inputSchema = "public"
            }
            generate {}
            target {
                packageName = "org.pokemmo.db.jooq"
                directory = "src/main/java"
            }
        }
    }
}
