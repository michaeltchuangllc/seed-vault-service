plugins {
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.ktlint).apply(false)
    alias(libs.plugins.room).apply(false)
}

allprojects {
    afterEvaluate {
        tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask> {
            exclude("**/generated/**")
            exclude("**/build/**")
        }
        tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask> {
            exclude("**/generated/**")
            exclude("**/build/**")
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
