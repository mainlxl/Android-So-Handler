package com.imf.plugin.so

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.ExtendedContentType
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import java.io.File
import java.util.concurrent.Executors

class SoFileTransform(val extension: SoFileExtensions, val intermediatesDir: File) : Transform() {
    override fun getName(): String = "soFileTransform"

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return mutableSetOf(ExtendedContentType.NATIVE_LIBS)
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> =
        TransformManager.SCOPE_FULL_PROJECT;

    override fun isIncremental(): Boolean = false

    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        transformInvocation ?: return
        val outputProvider = transformInvocation.outputProvider
        if (!isIncremental) {
            outputProvider.deleteAll()
        }
        val variantName = transformInvocation.context.getVariantName()
        var isRetainAll = isRetainAllSoFileByVariantName(variantName)
        //如果没有配置删除或者压缩则保留全部
        if (!isRetainAll && extension.deleteSoLibs.isNullOrEmpty() && extension.compressSo2AssetsLibs.isNullOrEmpty()) {
            isRetainAll = true
        }
        val soHandle = SoHandle(extension, AssetsOutDestManager(variantName, intermediatesDir))
        println("isRetainAll:${isRetainAll}")
        val executor = Executors.newFixedThreadPool(10)
        transformInvocation.inputs.forEach { input: TransformInput ->
            input.directoryInputs.forEach { directoryInput: DirectoryInput ->
                val dest: File = outputProvider.getContentLocation(
                    directoryInput.name,
                    directoryInput.contentTypes,
                    directoryInput.scopes,
                    Format.DIRECTORY
                )
                val dir: File = directoryInput.file
                if (dir.exists() && dir.isDirectory) {
                    val file = File(dir, "lib")
                    if (isRetainAll || !file.exists()) {//如果RESOURCES资源则lib文件夹则不存在
                        FileUtils.copyDirectory(directoryInput.file, dest)
                    } else {
                        soHandle.perform7z(file, executor, File(dest, "lib"))
                    }
                }

            }
            //jar文件不包含so文件直接拷贝给下一个
            input.jarInputs.forEach { jarInput: JarInput ->
                val dest: File = outputProvider.getContentLocation(
                    jarInput.name,
                    jarInput.contentTypes,
                    jarInput.scopes,
                    Format.JAR
                )
                executor.execute {
                    FileUtils.copyFile(jarInput.file, dest)
                }
            }
        }
        executor.shutdown()
        //结果写入assets文件中
        soHandle.resultWriteToFile()
    }


    //根据编译模式确定是否全部保留
    fun isRetainAllSoFileByVariantName(variantName: String): Boolean {
        return extension.excludeBuildTypes?.map { it.lowercase() }
            ?.contains(variantName.lowercase()) ?: false
    }
}