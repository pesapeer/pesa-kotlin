pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pesa-kotlin"

include(":checkout")
if (!(extra.properties["skip.compose"]?.toString()?.toBoolean() ?: false)) {
    include(":checkout-ui")
}
