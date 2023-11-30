plugins {
    kotlin("jvm").version("1.9.0")
    id("com.github.johnrengelman.shadow").version("7.0.0")
}

group = "me.lucas"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    api("com.github.exerosis:mynt:1.1.0")
}

tasks.shadowJar {
    archiveFileName.set("${project.name}.jar")
    destinationDirectory.set(file("./Server"))
    manifest.attributes["Main-Class"] = "me.lucas.raft.MainKt"
}

tasks.build { dependsOn(tasks.shadowJar) }

tasks.compileKotlin {
    kotlinOptions.freeCompilerArgs = listOf(
        "-Xcontext-receivers", "-Xinline-classes",
        "-Xopt-in=kotlin.time.ExperimentalTime",
        "-Xopt-in=kotlin.contracts.ExperimentalContracts",
        "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
        "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        "-Xopt-in=kotlinx.coroutines.DelicateCoroutinesApi"
    )
}