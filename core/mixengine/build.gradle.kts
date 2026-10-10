plugins {
    id("gravasom.android.library")
}

// Motor de remix da Mixlirous no Android (F1). O Rust compila com cargo-ndk;
// as bindings Kotlin (UniFFI) ficam versionadas em src/main/kotlin e o .so em
// jniLibs por ABI. Sem rede: o audio_agent entra sem a feature "network".
android {
    namespace = "com.gravasom.core.mixengine"

    defaultConfig {
        ndk {
            // arm64-v8a sempre; x86_64 só em debug (emulador). O release final
            // do app é arm64-v8a exclusivo (regra do plano, F8).
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    sourceSets {
        getByName("main") {
            // Os .so compilados pelo cargo-ndk entram como jniLibs do módulo.
            jniLibs.srcDir(layout.buildDirectory.dir("rust-jniLibs"))
        }
    }
}

// ---------- Compilação do Rust com cargo-ndk ----------

val rustDir = rootProject.layout.projectDirectory.dir("rust")
val rustTargetDir = rustDir.dir("target")
val cargo = System.getenv("CARGO") ?: "cargo"

/**
 * Compila o crate mixlirous_mobile para as ABIs pedidas via cargo-ndk.
 * arm64-v8a em release (device real) e x86_64 em debug (emulador).
 */
fun rustBuildTask(variant: String, release: Boolean, abis: List<String>): Exec =
    tasks.register<Exec>("buildRustMixlirous${variant.replaceFirstChar { it.uppercase() }}") {
        group = "mixlirous"
        description = "cargo-ndk build ($variant) → ${abis.joinToString()}"
        workingDir(rustDir)
        environment("CARGO_TARGET_DIR", rustTargetDir.asFile.path)
        val perfil = if (release) "--release" else ""
        val libDir = rustTargetDir.dir(if (release) "release" else "debug").asFile.path
        outputs.dir(layout.buildDirectory.dir("rust-jniLibs"))
        inputs.dir(rustDir.dir("mixlirous_mobile/src"))
        inputs.file(rustDir.file("mixlirous_mobile/Cargo.toml"))
        inputs.file(rustDir.file("Cargo.toml"))
        commandLine(
            cargo, "ndk",
            *abis.flatMap { listOf("-t", it) }.toTypedArray(),
            "-o", layout.buildDirectory.dir("rust-jniLibs").get().asFile.path,
            "build", perfil,
            "--manifest-path", rustDir.file("mixlirous_mobile/Cargo.toml").asFile.path,
        )
        doLast {
            // cargo-ndk já copia libmixlirous_mobile.so para o jniLibs de cada ABI.
            logger.lifecycle("Rust ($variant) compilado em $libDir")
        }
    }

val rustDebug = rustBuildTask("debug", release = false, abis = listOf("arm64-v8a", "x86_64"))
val rustRelease = rustBuildTask("release", release = true, abis = listOf("arm64-v8a"))

// O build de cada variante depende do Rust correspondente.
tasks.matching { it.name == "mergeDebugJniLibFolders" || it.name == "mergeDebugNativeLibs" || it.name.startsWith("mergeDebugNativeLibs") }
    .configureEach { dependsOn(rustDebug) }
tasks.matching { it.name == "mergeReleaseJniLibFolders" || it.name.startsWith("mergeReleaseNativeLibs") }
    .configureEach { dependsOn(rustRelease) }

// Regenera as bindings UniFFI quando a API do crate muda (comando manual:
// ./gradlew :core:mixengine:gerarBindings — mantidas versionadas no git).
tasks.register<Exec>("gerarBindings") {
    group = "mixlirous"
    description = "uniffi-bindgen generate --language kotlin"
    workingDir(rustDir.dir("mixlirous_mobile"))
    environment("CARGO_TARGET_DIR", rustTargetDir.asFile.path)
    commandLine(
        cargo, "run", "--bin", "uniffi-bindgen", "--",
        "generate",
        "--library", rustTargetDir.file("debug/libmixlirous_mobile.so").asFile.path,
        "--language", "kotlin",
        "--out-dir", projectDir.resolve("src/main/kotlin").path,
    )
}

dependencies {
    testImplementation(libs.junit4)
}
