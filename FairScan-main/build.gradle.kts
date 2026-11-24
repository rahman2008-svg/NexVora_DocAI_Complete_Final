// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.aboutLibrariesAndroid) apply false
    alias(libs.plugins.license)
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

// Files written by NexVora Lab's Ofc carry their own GPL-3.0-or-later header
// ("Copyright 2026 NexVora Lab's Ofc") and are therefore excluded from the FairScan header check/format,
// which would otherwise rewrite them with the upstream copyright line. Upstream files are untouched.
val nexvoraAuthoredKotlin = listOf("**/com/nexvora/docai/core/**", "**/com/nexvora/docai/feature/**")

license {
    header = rootProject.file("LICENSE_HEADER")
    exclude("**/*.xml")
    //strictCheck = true
    mapping("java", "SLASHSTAR_STYLE")
    mapping("kt", "SLASHSTAR_STYLE")
}

// See https://github.com/hierynomus/license-gradle-plugin/issues/155
tasks.register("licenseCheckForKotlin", com.hierynomus.gradle.license.tasks.LicenseCheck::class) {
    source = fileTree(project.projectDir) { include("**/*.kt"); exclude(nexvoraAuthoredKotlin) }
}
tasks["license"].dependsOn("licenseCheckForKotlin")
tasks.register("licenseFormatForKotlin", com.hierynomus.gradle.license.tasks.LicenseFormat::class) {
    source = fileTree(project.projectDir) { include("**/*.kt"); exclude(nexvoraAuthoredKotlin) }
}
tasks["licenseFormat"].dependsOn("licenseFormatForKotlin")
