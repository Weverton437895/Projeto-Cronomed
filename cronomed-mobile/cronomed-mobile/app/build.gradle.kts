plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "br.com.cronomed"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.cronomed"
        minSdk = 24 // Android 7.0 ou superior (como informado na landing page)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        // Avisos do lint não devem impedir o aluno de gerar o APK
        abortOnError = false
    }
}

dependencies {
    // AndroidX / Material Design (AppCompatActivity, FloatingActionButton, TextInputLayout...)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.biometric:biometric:1.1.0")

    // Firebase: o BoM mantém as versões compatíveis entre si
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")       // Firebase Authentication (e-mail e senha)
    implementation("com.google.firebase:firebase-firestore")  // Cloud Firestore (dados do tratamento)
}
