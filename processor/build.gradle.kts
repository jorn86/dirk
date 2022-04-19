plugins {
    kotlin("jvm")
}

buildscript {
    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.6.10"))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api("javax.inject:javax.inject:1")
    api("javax.annotation:javax.annotation-api:1.3.2")
    implementation(kotlin("reflect", "1.6.10"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.5.31-1.0.1")
    implementation("com.squareup:kotlinpoet:1.11.0")
}
