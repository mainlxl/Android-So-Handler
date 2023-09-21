package com.imf.plugin.so

import com.android.build.gradle.AppExtension
import com.google.gradle.osdetector.OsDetector
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.*
import java.util.Collections
import java.util.stream.Collectors
import java.util.zip.ZipFile

fun log(msg: Any) {
//        project.logger.info("[ApkSoLibStreamlineTask]: ${msg}")
    println("[SoFilePlugin]: ${msg}")
}

abstract class SoFilePlugin : Plugin<Project> {
    lateinit var intermediatesDir: File;
    lateinit var android: AppExtension;
    lateinit var pluginConfig: SoFileExtensions
    override fun apply(project: Project) {
        pluginConfig = project.extensions.create("SoFileConfig", SoFileExtensions::class.java)
        android = project.extensions.getByType(AppExtension::class.java)
        intermediatesDir = File(project.buildDir, "intermediates")
        if (pluginConfig.exe7zName.isEmpty()) {
            project.apply(mapOf("plugin" to "com.google.osdetector"))
        }
        project.afterEvaluate {
            log("enable: ${pluginConfig.enable}")
            if (pluginConfig.enable == true) {
                if (pluginConfig.exe7zName.isEmpty()) {
                    pluginConfig.exe7zName = find7zPath(project)
                }
                log("p7z path: ${pluginConfig.exe7zName}")
                afterProjectEvaluate(it)
            }
        }
    }

    private fun find7zPath(project: Project): String {
        val p7zConfig = project.configurations.create("ApkSoFileAdjustLocatorP7z") {
            it.isVisible = false
            it.isTransitive = false
            it.setExtendsFrom(Collections.emptyList())
        }

        val osdetector = project.extensions.getByType(OsDetector::class.java)

        val dep = project.dependencies.add(
            p7zConfig.name, mapOf<String, String>(
                "group" to "com.github.mainlxl.Android-So-Handler",
                "name" to "p7z",
                "classifier" to osdetector.classifier,
                "version" to "1.0.1",
                "ext" to "exe"
            )
        )
        runCatching {
            val file = p7zConfig.fileCollection(dep).singleFile
            if (!file.canExecute() && !file.setExecutable(true)) {
                throw GradleException("Cannot set ${file} as executable")
            }
            return file.absolutePath
        }
        // 兜底使用系统环境变量配置的7z命令
        val os = System.getenv("OS")?.lowercase()
        if (os != null && os.contains("windows")) {
            return "7z.exe"
        }
        return "7z"
    }


    protected open fun afterProjectEvaluate(project: Project) {
        val defaultConfig = android.defaultConfig
        pluginConfig.abiFilters = defaultConfig.ndk.abiFilters
        val minSdkVersion: Int = defaultConfig.minSdkVersion?.apiLevel ?: 0
        pluginConfig.neededRetainAllDependencies =
            pluginConfig.forceNeededRetainAllDependencies ?: (minSdkVersion <= 23)
    }
}

class SoFileTransformPlugin : SoFilePlugin() {
    override fun apply(project: Project) {
        super.apply(project)
        android.registerTransform(SoFileTransform(pluginConfig, intermediatesDir))
    }
}

@Deprecated("")
class SoFileAttachMergeTaskPlugin : SoFilePlugin() {
    override fun afterProjectEvaluate(project: Project) {
        super.afterProjectEvaluate(project)
        val buildTypes: MutableSet<String> = android.buildTypes.stream().map { it.name }.filter {
            if (pluginConfig.excludeBuildTypes.isNullOrEmpty()) {
                true
            } else {
                !pluginConfig.excludeBuildTypes!!.map { it.lowercase() }.contains(it.lowercase())
            }
        }.collect(Collectors.toSet())
        if (!buildTypes.isEmpty()) {
            val tasks = project.tasks
            buildTypes.forEach { variantName: String ->
                val upperCaseName =
                    variantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                val taskName = "merge${upperCaseName}NativeLibs"
                val mergeNativeLibsTask = tasks.findByName(taskName)
                if (mergeNativeLibsTask == null) {
                    tasks.forEach {
                        if (it.name.equals(taskName)) {
                            it.doLast(
                                SoFileVariantAction(
                                    variantName, pluginConfig, intermediatesDir
                                )
                            )
                        }
                    }
                } else {
                    mergeNativeLibsTask.doLast(
                        SoFileVariantAction(
                            variantName, pluginConfig, intermediatesDir
                        )
                    )
                }
            }
        }
    }
}


class ApkSoFileAdjustPlugin : SoFilePlugin() {
    override fun afterProjectEvaluate(project: Project) {
        super.afterProjectEvaluate(project)
        android.applicationVariants.all { variant ->
            val variantName = variant.name
            createTask(project, variantName)
        }

        android.buildTypes.all { buildType ->
            val buildTypeName = buildType.name
            createTask(project, buildTypeName)
        }

        android.productFlavors.all { flavor ->
            val flavorName = flavor.name
            createTask(project, flavorName)
        }
    }

    private fun createTask(project: Project, variantName: String) {
        val capitalizeVariantName =
            variantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val taskName = "ApkSoFileAdjust${capitalizeVariantName}"
        val excludeBuildTypes = pluginConfig.excludeBuildTypes
        if (!excludeBuildTypes.isNullOrEmpty()) {
            if (excludeBuildTypes.map { it.lowercase() }
                    .firstOrNull { variantName.lowercase().contains(it) } != null) {
                return
            }
        }
        if (project.tasks.findByPath(taskName) == null) {
            val tasks =
                setOf(project.tasks.findByPath("package${capitalizeVariantName}")).filterNotNull()
            if (tasks.isEmpty()) {
                return
            }
            val task = project.tasks.create(
                taskName,
                ApkSoLibStreamlineTask::class.java,
                android,
                pluginConfig,
                intermediatesDir,
                variantName
            )
            task.group = "Build"
            task.description = "进行so共享库处理"
            task.setMustRunAfter(tasks)
            project.tasks.findByPath("assemble${capitalizeVariantName}")?.dependsOn?.add(task)
        }
    }
}
