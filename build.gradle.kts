plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:3.3")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.Client.Client"
        )
    }

    // This will include all runtime dependencies in the JAR
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    // Handle duplicate files
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}