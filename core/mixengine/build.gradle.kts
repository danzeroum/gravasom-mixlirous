plugins {
    id("gravasom.android.library")
}

// Motor de remix da Mixlirous no Android (F1). O Rust compila com cargo-ndk;
// as bindings Kotlin (UniFFI) ficam versionadas em src/main/kotlin e o .so em
// jniLibs por ABI. Sem rede: o audio_agent entra sem a feature "network".
// Maquinas restritas (disco apertado) podem setar -PabiArm64Somente=true
// (ou no ~/.gradle/gradle.properties) para pular o x86_64; CI/emulador não usa
// a propriedade e continua com as duas ABIs.
val abisDebug = if (providers.gradleProperty("abiArm64Somente").isPresent) {
    listOf("arm64-v8a")
} else {
    listOf("arm64-v8a", "x86_64")
}

android {
    namespace = "com.gravasom.core.mixengine"

    defaultConfig {
        ndk {
            // arm64-v8a sempre; x86_64 só em debug (emulador). O release final
            // do app é arm64-v8a exclusivo (regra do plano, F8).
            abiFilters += abisDebug
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
 *
 * O retorno é TaskProvider<Exec>: tasks.register devolve um provider, não a
 * tarefa — tipar como Exec não compila (bug de configuração corrigido).
 */
fun rustBuildTask(variant: String, release: Boolean, abis: List<String>): TaskProvider<Exec> =
    tasks.register<Exec>("buildRustMixlirous${variant.replaceFirstChar { it.uppercase() }}") {
        group = "mixlirous"
        description = "cargo-ndk build ($variant) → ${abis.joinToString()}"
        workingDir(rustDir)
        environment("CARGO_TARGET_DIR", rustTargetDir.asFile.path)
        val perfil = if (release) "--release" else ""
        val libDir = rustTargetDir.dir(if (release) "release" else "debug").asFile.path
        val jniLibsOut = layout.buildDirectory.dir("rust-jniLibs")
        val stamp = layout.buildDirectory.file("rust/.so-built.stamp")
        outputs.dir(jniLibsOut)
        inputs.dir(rustDir.dir("mixlirous_mobile/src"))
        inputs.file(rustDir.file("mixlirous_mobile/Cargo.toml"))
        inputs.file(rustDir.file("Cargo.toml"))
        commandLine(
            cargo, "ndk",
            *abis.flatMap { listOf("-t", it) }.toTypedArray(),
            "-o", jniLibsOut.get().asFile.path,
            "build", perfil,
            "--manifest-path", rustDir.file("mixlirous_mobile/Cargo.toml").asFile.path,
        )
        // Economia de disco/tempo: com o stamp presente E todas as .so das ABIs
        // pedidas já no jniLibs do módulo, o Rust NÃO recompila (passo 2.3 do
        // roteiro). clean() apaga stamp e .so → rebuild volta a acontecer.
        onlyIf {
            val soPresentes = abis.all { abi ->
                jniLibsOut.get().dir(abi).file("libmixlirous_mobile.so").asFile.exists()
            }
            !(stamp.get().asFile.exists() && soPresentes)
        }
        doLast {
            // cargo-ndk já copia libmixlirous_mobile.so para o jniLibs de cada ABI.
            stamp.get().asFile.parentFile.mkdirs()
            stamp.get().asFile.writeText("built at ${System.currentTimeMillis()}")
            logger.lifecycle("Rust ($variant) compilado em $libDir")
        }
    }

val rustDebug = rustBuildTask("debug", release = false, abis = abisDebug)
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
    // Runtime UniFFI (bindings Kotlin geradas): JNA é obrigatório — em Android no
    // empacotamento @aar (inclui as libJNA nativas), em testes JVM o jar normal.
    implementation(libs.jna) { artifact { type = "aar" } }
    testImplementation(libs.jna)
    testImplementation(libs.junit4)
}
