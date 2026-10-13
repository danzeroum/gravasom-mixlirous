plugins {
    id("gravasom.android.library")
}

// RNNoise v0.2: baixado por hash (corpus/rnnoise.sha256) para build/rnnoise, antes de qualquer build nativo.
// Variante do modelo: ver docs/adr/015-reducao-de-ruido.md.
val rnnoiseVariant = "completo"
val rnnoiseDir = layout.buildDirectory.dir("rnnoise")
val fetchRnnoise = tasks.register<Exec>("fetchRnnoise") {
    val root = rootProject.layout.projectDirectory
    inputs.file(root.file("corpus/rnnoise.sha256"))
    inputs.file(root.file("scripts/buscar-rnnoise.sh"))
    inputs.property("variant", rnnoiseVariant)
    outputs.dir(rnnoiseDir)
    commandLine(root.file("scripts/buscar-rnnoise.sh").asFile.path, rnnoiseDir.get().asFile.path, rnnoiseVariant)
}

android {
    namespace = "com.gravasom.core.dsp"

    // Mesmo contrato do core/whisper: em máquinas restritas, -PabiArm64Somente=true limita o
    // nativo ao arm64 (o app só empacota arm64/x86_64 de qualquer jeito — os outros ABIs eram
    // compilados à toa e depois descartados pelo filtro de empacotamento do app).
    val abisNativas = if (providers.gradleProperty("abiArm64Somente").isPresent) {
        listOf("arm64-v8a")
    } else {
        listOf("arm64-v8a", "x86_64")
    }

    defaultConfig {
        ndk { abiFilters += abisNativas }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared", "-DRNNOISE_DIR=${rnnoiseDir.get().asFile.path}")
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

tasks.matching { it.name.startsWith("configureCMake") || it.name.startsWith("buildCMake") || it.name.startsWith("generateJsonModel") }
    .configureEach { dependsOn(fetchRnnoise) }

dependencies {
    implementation(project(":core:audio-engine"))

    testImplementation(libs.junit4)
}

// O harness do VAD (ADR-014) roda o MESMO codigo Kotlin + JNI no host: libgravasom_dsp.so de host, compilada por
// scripts/build-dsp-host.sh. Os dois caminhos vem do ambiente; sem eles os testes de corpus sao ignorados.
tasks.withType<Test>().configureEach {
    System.getenv("GRAVASOM_DSP_HOST_LIB")?.let { systemProperty("java.library.path", it) }
    System.getenv("GRAVASOM_VAD_CORPUS")?.let { systemProperty("gravasom.vad.corpus", it) }
    System.getenv("GRAVASOM_VAD_REPORT")?.let { systemProperty("gravasom.vad.report", it) }
    // O corpus e as lib de host mudam fora do Gradle: nao reaproveitar resultado em cache.
    inputs.property("vadCorpus", System.getenv("GRAVASOM_VAD_CORPUS") ?: "")
    inputs.property("ruidoCorpus", System.getenv("GRAVASOM_ONDA2_RUIDO_IN") ?: "")
    outputs.upToDateWhen { System.getenv("GRAVASOM_VAD_CORPUS") == null && System.getenv("GRAVASOM_ONDA2_RUIDO_IN") == null }
}
