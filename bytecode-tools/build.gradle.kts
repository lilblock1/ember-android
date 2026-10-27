plugins { application }
java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
application { mainClass = "dev.ember.bytecode.ArrayIntersectionAdapter" }
dependencies {
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
    implementation("org.ow2.asm:asm-analysis:9.7.1")
    testImplementation("junit:junit:4.13.2")
}
sourceSets["test"].java {
    srcDir(rootProject.file("tests/fixtures"))
    include("**/ArrayIntersectionAdapterTest.java", "**/EnumIntersectionFixture.java")
}
