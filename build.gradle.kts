import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
}

val releaseVersion = providers.gradleProperty("version").orNull?.takeIf { it != "unspecified" } ?: "0.1.0-SNAPSHOT"

allprojects {
    group = "co.pesa"
    version = releaseVersion
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral(automaticRelease = false)
            if (providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent) {
                signAllPublications()
            }
            coordinates("co.pesa", project.name, project.version.toString())
            pom {
                name.set("Pesa Checkout ${project.name}")
                description.set(project.description ?: "Pesa Checkout SDK")
                url.set("https://github.com/pesapeer/pesa-kotlin")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/license/mit")
                    }
                }
                developers {
                    developer {
                        id.set("pesapeer")
                        name.set("PesaPeer Ltd")
                        email.set("developer@pesapeer.com")
                    }
                }
                scm {
                    url.set("https://github.com/pesapeer/pesa-kotlin")
                    connection.set("scm:git:https://github.com/pesapeer/pesa-kotlin.git")
                    developerConnection.set("scm:git:ssh://git@github.com/pesapeer/pesa-kotlin.git")
                }
            }
        }
    }
}
