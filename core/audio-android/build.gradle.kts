plugins {
    id("gravasom.android.library")
    id("gravasom.android.hilt")
}

android {
    namespace = "com.gravasom.core.audio.android"

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
                arguments += listOf("-DANDROID_STL=c++_shared")
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

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:audio-engine"))
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    implementation(libs.tracing.ktx)
}
