plugins { id("com.android.library"); kotlin("android") }
val upstreamReference by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
android {
    namespace = "dev.andes.lwjgl"
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
        providers.gradleProperty("nativeBuildDir").orNull?.let { buildStagingDirectory = file("$it/lwjgl") }
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/lwjgl-assets"))
    testOptions.unitTests.all { it.systemProperty("lwjgl.reference.classpath", upstreamReference.asPath) }
}
dependencies {
    upstreamReference("org.lwjgl:lwjgl-glfw:3.3.2")
    upstreamReference("org.lwjgl:lwjgl-opengl:3.3.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
val compileLwjglFixture by tasks.registering(JavaCompile::class) {
    source(rootProject.file("tests/fixtures/LwjglBoundaryFixture.java"), rootProject.file("tests/fixtures/NativeMemoryFixture.java"))
    classpath = upstreamReference // Reference jars are NEVER packaged in this Android module.
    options.release = 17
    destinationDirectory = layout.buildDirectory.dir("fixture-classes")
    javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(17) }
}
val sdkDirectory = androidComponents.sdkComponents.sdkDirectory
val fixtureAssets = layout.buildDirectory.dir("generated/lwjgl-assets/lwjgl-fixture")
val dexLwjglFixture by tasks.registering(JavaExec::class) {
    dependsOn(compileLwjglFixture)
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }
    classpath = files(sdkDirectory.map { it.file("build-tools/35.0.0/lib/d8.jar") })
    mainClass = "com.android.tools.r8.D8"
    inputs.dir(compileLwjglFixture.flatMap { it.destinationDirectory })
    inputs.files(upstreamReference)
    outputs.dir(fixtureAssets)
    doFirst {
        fixtureAssets.get().asFile.mkdirs()
        args = listOf("--min-api", "29", "--lib",
            sdkDirectory.get().file("platforms/android-35/android.jar").asFile.path) +
            upstreamReference.files.flatMap { listOf("--classpath", it.path) } +
            listOf("--output", fixtureAssets.get().asFile.path) +
            compileLwjglFixture.get().destinationDirectory.get().asFile.walkTopDown()
                .filter { it.isFile && it.extension == "class" }.map { it.path }.toList()
    }
}
tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" || it.name.contains("Lint", ignoreCase = true) }
    .configureEach { dependsOn(dexLwjglFixture) }
