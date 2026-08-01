import org.gradle.api.tasks.bundling.Zip

plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "br.com.supermidia"
version = "1.0.0"

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

val appDisplayName = "SuperMidia MIDI Player"
val appModule = "br.com.supermidia"
val appMainClass = "br.com.supermidia.app.SuperMidiaApplication"
val appDescription = "Player MIDI para apresentações ao vivo"
val appVendor = "SuperMidia Alfenas"
val isWindows = System.getProperty("os.name").lowercase().contains("win")
val isLinux = System.getProperty("os.name").lowercase().contains("linux")
val installLibDirectory = layout.buildDirectory.dir("install/${project.name}/lib")
val windowsPackageDirectory = layout.buildDirectory.dir("packages/windows")
val linuxPackageDirectory = layout.buildDirectory.dir("packages/linux")
val linuxIcon = layout.projectDirectory.file(
    "src/main/resources/br/com/supermidia/app/supermidia-logo.png"
)
// O Windows exige .ico, com as várias resoluções que o sistema usa em cada contexto;
// o PNG do Linux não serve aqui. Sem este arquivo o jpackage aplica o ícone padrão
// do Java, e o atalho aparece com o mascote da linguagem em vez da marca.
val windowsIcon = layout.projectDirectory.file(
    "src/main/resources/br/com/supermidia/app/supermidia-logo.ico"
)
val jpackageExecutable = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
}.map { launcher ->
    val executable = if (isWindows) "jpackage.exe" else "jpackage"
    launcher.metadata.installationPath.file("bin/$executable").asFile
}

fun commonJpackageArguments(type: String, destination: String): List<String> = listOf(
    "--type", type,
    "--name", appDisplayName,
    "--app-version", version.toString(),
    "--vendor", appVendor,
    "--description", appDescription,
    "--dest", destination,
    "--module-path", installLibDirectory.get().asFile.absolutePath,
    "--module", "$appModule/$appMainClass",
    "--java-options", "-Dfile.encoding=UTF-8"
)

val cleanWindowsPackages by tasks.registering(Delete::class) {
    delete(windowsPackageDirectory)
}

val cleanLinuxPackages by tasks.registering(Delete::class) {
    delete(linuxPackageDirectory)
}

val packageWindowsAppImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Gera a aplicação portátil e autocontida para Windows."
    dependsOn(cleanWindowsPackages, tasks.installDist)
    enabled = isWindows
    executable(jpackageExecutable.get())
    args(
        commonJpackageArguments("app-image", windowsPackageDirectory.get().asFile.absolutePath)
            + listOf("--icon", windowsIcon.asFile.absolutePath)
    )
}

val packageWindowsPortable by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Compacta a aplicação portátil do Windows em um arquivo ZIP."
    dependsOn(packageWindowsAppImage)
    into(appDisplayName) {
        from(windowsPackageDirectory.map { it.dir(appDisplayName) })
    }
    archiveFileName.set("SuperMidia-MIDI-Player-${version}-windows-x64.zip")
    destinationDirectory.set(windowsPackageDirectory)
}

tasks.register<Exec>("packageWindowsInstaller") {
    group = "distribution"
    description = "Gera o instalador EXE do Windows (requer WiX Toolset)."
    dependsOn(packageWindowsAppImage)
    enabled = isWindows
    executable(jpackageExecutable.get())
    args(
        "--type", "exe",
        "--name", appDisplayName,
        "--app-version", version.toString(),
        "--vendor", appVendor,
        "--description", appDescription,
        "--dest", windowsPackageDirectory.get().dir("installer").asFile.absolutePath,
        "--app-image", windowsPackageDirectory.get().dir(appDisplayName).asFile.absolutePath,
        "--icon", windowsIcon.asFile.absolutePath,
        "--win-per-user-install",
        "--win-dir-chooser",
        "--win-menu",
        "--win-menu-group", "SuperMidia",
        "--win-shortcut"
    )
}

tasks.register<Exec>("packageUbuntuDeb") {
    group = "distribution"
    description = "Gera o instalador DEB para Ubuntu, Mint e derivados."
    dependsOn(cleanLinuxPackages, tasks.installDist)
    enabled = isLinux
    executable(jpackageExecutable.get())
    args(
        commonJpackageArguments("deb", linuxPackageDirectory.get().asFile.absolutePath) + listOf(
            "--icon", linuxIcon.asFile.absolutePath,
            "--linux-package-name", "supermidia-midi-player",
            "--linux-deb-maintainer", "denisantoniorocha@gmail.com",
            "--linux-menu-group", "AudioVideo",
            "--linux-app-category", "sound",
            "--linux-shortcut"
        )
    )
}
