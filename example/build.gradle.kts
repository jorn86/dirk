plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.5.31-1.0.1"
    application
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":processor"))
    ksp(project(":processor"))
}

application {
    mainClass.set("org.hertsig.app.AppKt")
}
