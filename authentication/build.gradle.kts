import java.util.Properties

plugins { id("com.android.library"); kotlin("android") }
val authProperties = Properties().apply {
    rootProject.file("auth.local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun authValue(key: String) = authProperties.getProperty(key, "")
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
android {
    namespace = "dev.andes.authentication"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
        buildConfigField("String", "CLIENT_ID", quoted(authValue("microsoft.clientId")))
        buildConfigField("String", "REGISTRATION_REFERENCE", quoted(authValue("microsoft.registrationReference")))
        buildConfigField("String", "SERVICES_APPROVAL_REFERENCE", quoted(authValue("minecraft.approvalReference")))
        buildConfigField("String", "SERVICES_OAUTH_SCOPES", quoted(authValue("minecraft.oauthScopes")))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "dev.andes.androidjavaport"
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core"))
    implementation("net.openid:appauth:0.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
