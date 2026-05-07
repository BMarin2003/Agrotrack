plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.hilt)                apply false
    // TODO: Agregar google-services una vez que tengas google-services.json de Firebase Console
    // id("com.google.gms.google-services") version "4.4.2" apply false
}
