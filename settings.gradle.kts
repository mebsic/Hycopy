plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Hycopy"
include("core", "murdermystery", "proxy", "build")

if (file("private/injector/build.gradle.kts").isFile) {
    include("injector")
    project(":injector").projectDir = file("private/injector")
}
