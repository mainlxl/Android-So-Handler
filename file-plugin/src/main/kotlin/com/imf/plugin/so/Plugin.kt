package com.imf.plugin.so

import com.android.build.gradle.AppExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.*
import java.util.stream.Collectors

abstract class SoFilePlugin : Plugin<Project> {
    lateinit var intermediatesDir: File;
    lateinit var android: AppExtension;
    lateinit var pluginConfig: SoFileExtensions
    override fun apply(project: Project) {
        pluginConfig = project.extensions.create("SoFileConfig", SoFileExtensions::class.java)
        android = project.extensions.getByType(AppExtension::class.java)
        intermediatesDir = File(project.buildDir, "intermediates")
        project.afterEvaluate {
            afterProjectEvaluate(it)
        }
    }

    protected open fun afterProjectEvaluate(project: Project) {
        val defaultConfig = android.defaultConfig
        pluginConfig.abiFilters = defaultConfig.ndk.abiFilters
        val os = System.getenv("OS")?.lowercase()
        if (os != null && os.contains("windows")) {
            pluginConfig.exe7zName = "7z.exe"
        }
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
                !pluginConfig.excludeBuildTypes!!.contains(it)
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

    fun createTask(project: Project, variantName: String) {
        val capitalizeVariantName =
            variantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val taskName = "ApkSoFileAdjust${capitalizeVariantName}"
        val excludeBuildTypes = pluginConfig.excludeBuildTypes
        if (!excludeBuildTypes.isNullOrEmpty()) {
            if (excludeBuildTypes.contains(variantName)) {
                return
            }
        }
        if (project.tasks.findByPath(taskName) == null) {
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
            task.dependsOn.add("package${capitalizeVariantName}")
            project.tasks.findByPath("assemble${capitalizeVariantName}")?.dependsOn?.add(task)

        }
    }
}
