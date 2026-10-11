plugins {
    id("gravasom.android.library")
    id("gravasom.android.compose")
    id("gravasom.android.hilt")
}

android {
    namespace = "com.gravasom.core.service"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:audio-engine"))
    implementation(project(":core:audio-android"))
    implementation(project(":core:data"))
    implementation(project(":core:dsp"))
    // Workers de analise/criacao Mixlirous (F2/F3) falam com o motor via MotorMixlirous.
    implementation(project(":core:mixengine"))
    implementation(libs.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.timber)
    implementation(libs.androidx.core.ktx)
    implementation(libs.glance.appwidget)
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    // O teste do backup exerce o cofre de verdade (VaultCrypto/Tink em fluxo); o de dividir/juntar abre o Room de verdade.
    testImplementation(libs.tink.android)
    testImplementation(libs.room.runtime)
    testImplementation(libs.room.ktx)
}
