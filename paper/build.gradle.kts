plugins {
    java
}

group = "net.unchartedsmp"
version = providers.gradleProperty("plugin_version").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${providers.gradleProperty("paper_api").get()}")

    // MockBukkit is a mock Paper 1.21.11 server; the plugin built against 1.21.4 runs on it.
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-Werror"))
}

tasks.compileTestJava {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

// The 3D models, textures and tooltip frame live in ../resourcepack (shared with the Fabric mod).
// They ship inside the plugin, which serves them to players, and as a standalone zip.
val resourcePackZip by tasks.registering(Zip::class) {
    from("../resourcepack")
    archiveFileName = "resourcepack.zip"
    destinationDirectory = layout.buildDirectory.dir("pack")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.jar {
    archiveBaseName = "Bloodbath"
    from(resourcePackZip)
    from("../LICENSE") {
        rename { "LICENSE_bloodbath" }
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.test {
    useJUnitPlatform()
    // The plugin serves the pack it finds on its classpath.
    dependsOn(resourcePackZip)
    classpath += files(layout.buildDirectory.dir("pack"))
}

val standaloneResourcePack by tasks.registering(Copy::class) {
    val name = "Bloodbath-ResourcePack-${project.version}.zip"
    from(resourcePackZip)
    into(layout.buildDirectory.dir("libs"))
    rename { name }
}

tasks.build {
    dependsOn(standaloneResourcePack)
}
