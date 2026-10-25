plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "dev.andes.compatibility"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    defaultConfig {
        minSdk = 29
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON" } }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    externalNativeBuild { cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
        providers.gradleProperty("nativeBuildDir").orNull?.let { buildStagingDirectory = file("$it/lab") }
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["androidTest"].assets.srcDir(layout.buildDirectory.dir("generated/fixture-assets"))
}

val compileFixture by tasks.registering(JavaCompile::class) {
    source(rootProject.file("tests/fixtures/CompatibilityFixture.java"))
    classpath = files()
    options.release = 17
    destinationDirectory = layout.buildDirectory.dir("fixture-classes")
    javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(17) }
}
val sdkDirectory = androidComponents.sdkComponents.sdkDirectory
val fixtureAssets = layout.buildDirectory.dir("generated/fixture-assets")
val dexFixture by tasks.registering(JavaExec::class) {
    dependsOn(compileFixture)
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }
    classpath = files(sdkDirectory.map { it.file("build-tools/35.0.0/lib/d8.jar") })
    mainClass = "com.android.tools.r8.D8"
    inputs.dir(compileFixture.flatMap { it.destinationDirectory })
    outputs.dir(fixtureAssets)
    doFirst {
        fixtureAssets.get().asFile.mkdirs()
        args = listOf("--min-api", "29", "--lib",
            sdkDirectory.get().file("platforms/android-35/android.jar").asFile.path,
            "--output", fixtureAssets.get().asFile.path,
            compileFixture.get().destinationDirectory.get().file("fixture/CompatibilityFixture.class").asFile.path)
    }
}
tasks.matching { it.name == "mergeDebugAndroidTestAssets" }.configureEach { dependsOn(dexFixture) }

dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
