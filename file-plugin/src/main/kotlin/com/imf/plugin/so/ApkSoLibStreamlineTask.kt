package com.imf.plugin.so

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.ide.common.internal.WaitableExecutor
import com.mainli.apk.ApkSign
import com.mainli.apk.ZipUtil
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject


open class ApkSoLibStreamlineTask @Inject constructor(
    val android: AppExtension,
    val pluginConfig: SoFileExtensions,
    val intermediatesDir: File,
    val variantName: String
) : DefaultTask() {

    @TaskAction
    fun run() {
        android.applicationVariants.all { variant ->
            if (variant.name == variantName) {
                var apkFile: File? = getApkFile(variant)
                if (apkFile == null) {
                    log("apk文件目录未找到定义,请升级插件")
                    System.exit(2)
                }
                val oldSize = apkFile!!.length()
                val newApk = streamlineApkSoFile(apkFile)
                if (newApk?.exists() == true) {
                    val signApk = ApkSign.sign(newApk, variant)
                    newApk.delete()
                    signApk.renameTo(apkFile)
                    val newSize = apkFile!!.length()
                    val oldSizeM = oldSize / 1024f / 1024f
                    val newSizeM = newSize / 1024f / 1024f
                    val changeSizeM = (oldSize - newSize) / 1024f / 1024f
                    log("处理前Apk Size:${oldSizeM}M, 处理后Apk Size:${newSizeM}M, 优化 Size${changeSizeM}M")
                }
            }
        }
    }

    private fun getApkFile(variant: ApplicationVariant): File? {
        var outputFile: File? = null
        variant.outputs.all { output: BaseVariantOutput ->
            try {
                outputFile = File(
                    variant.packageApplicationProvider.get().outputDirectory.get().asFile.absolutePath,
                    output.outputFile.name
                )
            } catch (e: Exception) {
            } finally {
                outputFile = outputFile ?: output.outputFile
            }
        }
        return outputFile
    }

    fun log(msg: Any) {
        project.logger.info("[ApkSoLibStreamlineTask]: ${msg}")
//        println("[ApkSoLibStreamlineTask]: ${msg}")
    }

    open fun streamlineApkSoFile(apk: File?): File? {
        if (apk == null || !apk.exists()) {
            return null
        }
        val outPutApk = File(apk.parentFile, "_streamlineApkBySoFile.apk")
        var inputZip: ZipFile? = null
        var zipOutputStream: ZipOutputStream? = null
        try {
            inputZip = ZipFile(apk)
            val inputEntries = inputZip.entries()
            zipOutputStream = ZipOutputStream(FileOutputStream(outPutApk))
            val streamlineFile = File(apk.canonicalFile.parentFile, "streamline")
            while (inputEntries.hasMoreElements()) {
                val inputZipEntry = inputEntries.nextElement()
                val name = inputZipEntry.name
                if (name.startsWith("lib/")) {
                    ZipUtil.unZipFile(inputZip, inputZipEntry, streamlineFile)
                    continue
                }
                ZipUtil.addZipFile2ZipOutputStream(inputZip, inputZipEntry, zipOutputStream)
            }

            val executor: WaitableExecutor = WaitableExecutor.useGlobalSharedThreadPool()
            val soHandle = SoHandle(variantName,
                pluginConfig,
                object : AssetsOutDestManager(variantName, intermediatesDir) {
                    override fun buildAssetsOutDestFile(): File {
                        return File(streamlineFile, JNI_LIBS)
                    }
                })
            soHandle.perform7z(
                File(streamlineFile, "lib"), executor, null
            )
            executor.waitForTasksWithQuickFail<Any?>(true);
            soHandle.resultWriteToFile()
            ZipUtil.addFile2ZipOutputStream(
                File(streamlineFile, "jniLibs"), "assets/jniLibs/", zipOutputStream
            )
            ZipUtil.addFile2ZipOutputStream(
                File(streamlineFile, "lib"), "lib/", zipOutputStream
            )
            zipOutputStream.flush()
            return outPutApk
        } catch (e: IOException) {
            e.printStackTrace()
            if (outPutApk.exists()) {
                outPutApk.delete()
            }
        } finally {
            ZipUtil.close(inputZip)
            ZipUtil.close(zipOutputStream)
        }
        return null
    }


}