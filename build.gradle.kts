plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "br.com.supermidia"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    modularity.inferModulePath.set(true)
}

javafx {
    version = "21.0.10"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainModule.set("br.com.supermidia")
    mainClass.set("br.com.supermidia.app.SuperMidiaApplication")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}
