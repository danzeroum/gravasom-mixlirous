plugins {
    id("gravasom.android.library")
}

// Motor de transcrição local (F7, ADR-013/017): whisper.cpp 1.8.5 hash-pinned compilado por CMake.
// A fonte NUNCA fica no git — scripts/buscar-whisper.sh baixa por SHA-256 antes de qualquer build
// nativo (mesma fonte/hash do spike ADR-013). Máquinas restritas usam -PabiArm64Somente=true.
val abisNativas = if (providers.gradleProperty("abiArm64Somente").isPresent) {
    listOf("arm64-v8a")
} else {
    listOf("arm64-v8a", "x86_64")
}

val whisperSrc = layout.projectDirectory.dir("third_party/whisper.cpp")
val buscarWhisper = tasks.register<Exec>("buscarWhisper") {
    group = "whisper"
    description = "Baixa whisper.cpp 1.8.5 por hash (spike ADR-013) em third_party/"
    inputs.file(layout.projectDirectory.file("scripts/buscar-whisper.sh"))
    outputs.dir(whisperSrc)
    // Sem onlyIf: com outputs.dir + inputs.file, o Gradle marca UP-TO-DATE quando a fonte
    // hash-pinned já está extraída (e não captura referências de script — configuration cache).
    commandLine(layout.projectDirectory.file("scripts/buscar-whisper.sh").asFile.path, whisperSrc.asFile.path)
}

android {
    namespace = "com.gravasom.core.whisper"

    defaultConfig {
        ndk { abiFilters += abisNativas }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DWHISPER_SRC=${whisperSrc.asFile.path}", "-DANDROID_STL=c++_shared", "-DGGML_OPENMP=OFF", "-DGGML_LLAMAFILE=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF", "-DWHISPER_BUILD_TESTS=OFF", "-DWHISPER_BUILD_SERVER=OFF", "-DBUILD_SHARED_LIBS=OFF",
                )
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
    .configureEach { dependsOn(buscarWhisper) }
