plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    compileOnly("org.json:json:20260814")
    testImplementation("org.json:json:20260814")
    testImplementation("junit:junit:4.13.2") }

