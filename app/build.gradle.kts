plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

// ---- Version: the top entry of CHANGELOG.md --------------------------------------------------------
// Every update adds a "## X.Y.Z — YYYY-MM-DD" entry there; Settings → About shows the whole list.
val changelogFile: File = rootProject.file("CHANGELOG.md")
val releaseVersion: List<Int> = run {
    val heading = changelogFile.readLines().firstOrNull { it.startsWith("## ") }
        ?: throw GradleException("CHANGELOG.md has no version entry. Add \"## X.Y.Z — YYYY-MM-DD\" at the top.")
    val match = Regex("""^## (\d+)\.(\d+)\.(\d+) [—-] \d{4}-\d{2}-\d{2}\s*$""").matchEntire(heading)
        ?: throw GradleException("CHANGELOG.md: \"$heading\" should look like \"## X.Y.Z — YYYY-MM-DD\".")
    match.groupValues.drop(1).map(String::toInt).also { (_, minor, patch) ->
        if (minor > 99 || patch > 99) throw GradleException("CHANGELOG.md: minor and patch must stay below 100 (versionCode = X*10000 + Y*100 + Z).")
    }
}

/** Ships CHANGELOG.md in the APK as assets/changelog.md, so the repo file stays the only copy. */
abstract class CopyChangelog : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val changelog: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        changelog.get().asFile.copyTo(File(dir, "changelog.md"))
    }
}

val copyChangelog = tasks.register<CopyChangelog>("copyChangelog") { changelog.set(changelogFile) }

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(copyChangelog, CopyChangelog::outputDir) }
}

android {
    namespace = "com.anda.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.anda.launcher"
        minSdk = 34
        targetSdk = 37
        versionCode = releaseVersion[0] * 10000 + releaseVersion[1] * 100 + releaseVersion[2]
        versionName = releaseVersion.joinToString(".")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal/sideload distribution: sign release with the debug key until a real keystore exists.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    lint {
        warningsAsErrors = true
        lintConfig = rootProject.file("lint.xml")
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
    reportsDestination.set(layout.buildDirectory.dir("compose_reports"))
    metricsDestination.set(layout.buildDirectory.dir("compose_metrics"))
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:drawer"))
    implementation(project(":feature:search"))
    implementation(project(":feature:gestures"))
    implementation(project(":feature:badges"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
