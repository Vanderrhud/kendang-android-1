plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Lokasi assets/kits diberikan lewat systemProperty Gradle untuk validasi kit bawaan
    systemProperty("assets.kits.dir", rootProject.file("app/src/main/assets/kits").absolutePath)
}
