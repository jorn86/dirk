plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.5.31-1.0.1"
    application
}

repositories {
    mavenCentral()
    maven {
        name = "Github Packages"
        url = uri("https://maven.pkg.github.com/jorn86/dirk")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
    mavenLocal()
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.hertsig.dirk:dirk:0.1.0")
    ksp("org.hertsig.dirk:dirk:0.1.0")
//    implementation(project(":processor"))
//    ksp(project(":processor"))
}

application {
    mainClass.set("org.hertsig.app.AppKt")
}
