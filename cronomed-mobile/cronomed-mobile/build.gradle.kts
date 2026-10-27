// Plugins declarados na raiz e aplicados no módulo :app
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Plugin do Google Services: lê o google-services.json (passo ensinado na aula de Firebase)
    id("com.google.gms.google-services") version "4.4.2" apply false
}
