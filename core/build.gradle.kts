plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    maven("https://maven.citizensnpcs.co/repo")
    maven("https://repo.codemc.io/repository/maven-snapshots/") {
        content { includeGroup("net.wesjd") }
    }
}

dependencies {
    implementation("org.mongodb:mongodb-driver-sync:4.11.1")
    implementation("redis.clients:jedis:5.1.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("net.wesjd:anvilgui:1.10.9-SNAPSHOT")
    compileOnly("net.citizensnpcs:citizensapi:2.0.30-SNAPSHOT")
}

tasks.shadowJar {
    relocate("net.wesjd.anvilgui", "io.github.mebsic.core.lib.anvilgui")
    archiveClassifier.set("")
    archiveBaseName.set("Hycopy")
    archiveVersion.set("")
    doFirst {
        archiveFile.get().asFile.delete()
    }
}

val copyShadowJarToDocker by tasks.registering(Copy::class) {
    dependsOn(tasks.shadowJar)
    val shadowArchive = tasks.shadowJar.flatMap { it.archiveFile }
    from(shadowArchive)
    into(rootProject.layout.projectDirectory.dir("docker/development/plugins"))
}

tasks.shadowJar {
    finalizedBy(copyShadowJarToDocker)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
