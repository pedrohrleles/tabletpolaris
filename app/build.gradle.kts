plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.polarisrh.tabletpolaris"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.polarisrh.tabletpolaris"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Cada tablet físico é instalado com uma variante travada num ambiente — nunca escolhido em
    // tempo de execução. "dev" fala com o servidor de homologação; "prod" com o servidor real.
    // Confirmado com o time do web: mesmos caminhos de endpoint nos dois, só muda o domínio, e
    // toda URL precisa terminar com "/" (o nginx redireciona 308 sem a barra, e nem todo client
    // HTTP segue redirect em POST automaticamente).
    flavorDimensions += "ambiente"
    productFlavors {
        create("dev") {
            dimension = "ambiente"
            applicationIdSuffix = ".dev"
            buildConfigField("String", "POLARIS_API_BASE_URL", "\"https://dev.polarisrh.com.br/api/\"")
        }
        create("prod") {
            dimension = "ambiente"
            // ATENÇÃO: confirmado com o time do web que esse domínio ainda não está no ar (404
            // em toda rota de coletor até 28/08/2026) — não instalar essa variante em tablet
            // nenhum antes de confirmarem que voltou 401/400 (rota existindo), igual já está na
            // dev. Configurado aqui só pra já deixar pronto.
            buildConfigField("String", "POLARIS_API_BASE_URL", "\"https://app.polarisrh.com.br/api/\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // MobileFaceNet.tflite precisa ir sem compressão no APK, senão o mmap direto do arquivo
    // (usado pra carregar o modelo) falha.
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.mlkit.face.detection)
    implementation(libs.tensorflow.lite)

    debugImplementation(libs.androidx.ui.tooling)
}
