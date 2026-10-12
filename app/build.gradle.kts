import java.util.Properties

plugins {
    id("gravasom.android.application")
    id("gravasom.android.compose")
    id("gravasom.android.hilt")
}

android {
    namespace = "com.gravasom"
    defaultConfig {
        // Identidade própria do Gravasom Mixlirous: applicationId novo instala ao lado do Gravasom original.
        // O namespace permanece com.gravasom para reutilizar todo o código herdado sem reescrever imports.
        applicationId = "com.gravasom.mixlirous"
        // Sem CI: a versao vem de app/version.properties (versionCode sobe a cada entrega). O workflow manual pode sobrepor por ambiente.
        val version = Properties().apply {
            load(providers.fileContents(layout.projectDirectory.file("version.properties")).asText.get().reader())
        }
        versionCode = System.getenv("GRAVASOM_VERSION_CODE")?.toIntOrNull() ?: version.getProperty("versionCode").toInt()
        versionName = System.getenv("GRAVASOM_VERSION_NAME")?.removePrefix("v") ?: version.getProperty("versionName")
        // Aparelhos reais (arm64) e o emulador do CI (x86_64). Reduz o APK; x86/armv7 de 32 bits ficam de fora.
        val abisNativas = if (providers.gradleProperty("abiArm64Somente").isPresent) {
            listOf("arm64-v8a")
        } else {
            listOf("arm64-v8a", "x86_64")
        }
        ndk { abiFilters += abisNativas }
    }
    signingConfigs {
        // Keystore de DEBUG fixo e publico (senha padrao "android"): o APK baixado da CI atualiza por cima do anterior.
        // NAO e uma chave de producao.
        getByName("debug") {
            storeFile = rootProject.file("config/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release: keystore e senhas SO por variaveis de ambiente (GitHub Secrets na CI); nada disso entra no repositorio.
        // Sem as variaveis, o release nao e assinado aqui e o job "release-apk" da CI recusa gerar o artefato.
        val keystorePath = System.getenv("GRAVASOM_KEYSTORE_FILE")
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("GRAVASOM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GRAVASOM_KEY_ALIAS")
                keyPassword = System.getenv("GRAVASOM_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            // Com chave de release (variaveis de ambiente) assina com ela; SEM ela, assina com a chave de debug do repositorio,
            // para que cada entrega instale por cima da anterior. A chave de debug e publica: nao e uma chave de producao.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        // Para o Macrobenchmark: igual ao release (R8, sem depuracao) mas assinado com a chave de debug. Traz o SeedReceiver
        // (app/src/benchmark) que semeia a gravacao de 1 h; esse receptor NAO existe no release.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:service"))
    implementation(project(":core:ui"))
    implementation(project(":core:dsp"))
    implementation(project(":core:mixengine"))
    implementation(project(":core:whisper"))
    implementation(project(":feature:record"))
    implementation(project(":feature:library"))
    implementation(project(":feature:player"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:enhance"))
    implementation(project(":feature:criar"))
    implementation(project(":feature:transcricao"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    implementation(libs.profileinstaller)
    implementation(libs.room.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.compose.material.icons.extended)

    androidTestImplementation(project(":core:audio-engine"))
    androidTestImplementation(project(":core:audio-android"))
    androidTestImplementation(project(":core:data"))
    androidTestImplementation(project(":core:dsp"))
    // O variant "benchmark" (Macrobenchmark) compila o sourceSet src/benchmark contra o mesmo motor.
    add("benchmarkImplementation", project(":core:audio-engine"))
    add("benchmarkImplementation", project(":core:domain"))
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.room.ktx)
    androidTestImplementation(libs.coroutines.android)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
