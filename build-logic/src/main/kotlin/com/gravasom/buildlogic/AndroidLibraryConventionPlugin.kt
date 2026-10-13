package com.gravasom.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("io.gitlab.arturbosch.detekt")

        extensions.configure<LibraryExtension> {
            compileSdk = 36
            // NDK FIXADO — ver comentário no AndroidApplicationConventionPlugin.
            ndkVersion = "27.2.12479018"
            defaultConfig {
                minSdk = 26
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                consumerProguardFiles("consumer-rules.pro")
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
