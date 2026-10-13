package com.gravasom.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("io.gitlab.arturbosch.detekt")

        extensions.configure<ApplicationExtension> {
            compileSdk = 36
            // NDK FIXADO: builds reprodutíveis em qualquer máquina (e o AGP não auto-baixa
            // o default dele por cima — no sandbox apertado de disco eram 2 GB de duplicata).
            ndkVersion = "27.2.12479018"
            defaultConfig {
                minSdk = 26
                targetSdk = 36
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                vectorDrawables { useSupportLibrary = true }
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            buildFeatures {
                buildConfig = true
            }
            lint {
                warningsAsErrors = true
                abortOnError = true
                // Verificacoes de "versao mais nova disponivel" dependem da rede e nao do codigo;
                // atualizar AGP/dependencias e decisao consciente, nao motivo para quebrar o build.
                disable += setOf("AndroidGradlePluginVersion", "NewerVersionAvailable", "GradleDependency")
            }
            buildTypes {
                release {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
            }
            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                }
            }
        }

        tasks.withType(KotlinCompile::class.java).configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.add("-Xjvm-default=all")
            }
        }
    }
}
