plugins {
    kotlin("jvm")
    id("maven-publish")
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

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/jorn86/dirk")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }

    publications {
        create<MavenPublication>("gpr") {
            groupId = "org.hertsig.dirk"
            artifactId = "dirk"
            version = "0.1.0-SNAPSHOT"
            from(components["java"])
        }
    }
}
