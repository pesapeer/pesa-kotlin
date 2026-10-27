import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val skipAndroid = (findProperty("skip.android") as String?).toBoolean()
val skipIos = (findProperty("skip.ios") as String?).toBoolean()

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

description = "Pesa Checkout Compose card fields"

kotlin {
    jvm()
    if (!skipAndroid) {
        androidTarget {
            publishLibraryVariants("release")
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }
    if (!skipIos) {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "PesaCheckoutUI"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":checkout"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        if (!skipAndroid) {
            androidMain.dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "co.pesa.checkout.ui"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
