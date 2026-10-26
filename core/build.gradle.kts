plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.jsoup)
    api(libs.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    // Тесты читают сохранённые страницы из samples/ в корне репозитория.
    systemProperty("samples.dir", rootProject.file("samples").absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
