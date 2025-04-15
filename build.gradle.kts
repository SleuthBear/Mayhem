plugins {
    id("java")
    kotlin("jvm")
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
    implementation(kotlin("stdlib-jdk8"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runClient") {
    description = "Runs the Client application"
    mainClass.set("com.Client.Client")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runServer") {
    description = "Runs the Server application"
    mainClass.set("com.Server.Server")
    classpath = sourceSets["main"].runtimeClasspath
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
kotlin {
    jvmToolchain(17)
}