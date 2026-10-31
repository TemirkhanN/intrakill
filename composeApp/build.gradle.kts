import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.koin.compiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.sqlite)
            implementation(libs.androidx.sqlite.framework)
            implementation(libs.android.database.sqlcipher)
            implementation(libs.ktor.server.android)
            implementation(libs.coil.gif)
            implementation(libs.koin.android)
        }
        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.jbcrypt)
            implementation(libs.decompose)
            implementation(libs.ext.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.multiplatform.media.player)
            implementation(libs.ktor.server.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.material.icons.core)
            implementation(libs.material.icons.extended)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.coil.compose)
            implementation(libs.qr.generator)
            implementation(libs.qr.scanner)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
            implementation(libs.koin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.desktop.database.sqlcipher)
            implementation(libs.ktor.server.desktop)
            implementation(libs.javacv.platform)
            implementation("net.java.dev.jna:jna:5.14.0")
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
        }
    }
}

android {
    namespace = "me.nasukhov.intrakill"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "me.nasukhov.intrakill"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.ui.tooling)
}

compose.desktop {
    application {
        mainClass = "me.nasukhov.intrakill.MainKt"

        nativeDistributions {
            modules("java.sql")

            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "me.nasukhov.intrakill"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn("ktlintCheck", "detekt")
}
