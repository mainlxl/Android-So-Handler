package com.imf.plugin.so

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.mainli.apk.ApkSign
import com.mainli.apk.ZipUtil
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject


open class ApkSoLibStreamlineTask @Inject constructor(
    @Internal val android: AppExtension,
    @Internal val pluginConfig: SoFileExtensions,
    @Internal val intermediatesDir: File,
    @Internal val variantName: String
) : DefaultTask() {

    @TaskAction
    fun run() {
        android.applicationVariants.all { variant ->
            if (variant.name == variantName) {
                val apkFile: File? = getApkFile(variant)
                if (apkFile == null) {
                    log("apk文件目录未找到定义,请升级插件")
                    System.exit(2)
                }
                val oldSize = apkFile!!.length()
                val newApk = streamlineApkSoFile(apkFile)
                if (newApk?.exists() == true) {
                    val signApk = ApkSign.sign(newApk, variant)
                    newApk.delete()
                    apkFile.renameTo(File(apkFile.parentFile, "backup-" + apkFile.name))
                    signApk.renameTo(apkFile)
                    val newSize = apkFile.length()
                    val oldSizeM = oldSize / 1024f / 1024f
                    val newSizeM = newSize / 1024f / 1024f
                    val changeSizeM = (oldSize - newSize) / 1024f / 1024f
                    log("处理前Apk Size: ${oldSizeM}M, 处理后Apk Size: ${newSizeM}M, 优化 Size: ${changeSizeM}M")
                }
            }
        }
    }

    private fun getApkFile(variant: ApkVariant): File? {
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

    open fun streamlineApkSoFile(apk: File?): File? {
        if (apk == null || !apk.exists()) {
            return null
        }
        val outPutApk = File(apk.parentFile, "_streamlineApkBySoFile.apk")
        if (outPutApk.exists()) {
            outPutApk.delete()
        }
        var inputZip: ZipFile? = null
        var zipOutputStream: ZipOutputStream? = null
        try {
            inputZip = ZipFile(apk)
            val inputEntries = inputZip.entries()
            zipOutputStream = ZipOutputStream(FileOutputStream(outPutApk))
            val streamlineFile = File(apk.canonicalFile.parentFile, "streamline")
            if (streamlineFile.exists()) {
                streamlineFile.delete()
            }
            while (inputEntries.hasMoreElements()) {
                val inputZipEntry = inputEntries.nextElement()
                val name = inputZipEntry.name
                if (name.startsWith("lib/")) {
                    ZipUtil.unZipFile(inputZip, inputZipEntry, streamlineFile)
                    continue
                }
                ZipUtil.addZipFile2ZipOutputStream(inputZip, inputZipEntry, zipOutputStream)
            }
            val soHandle = SoHandle(
                pluginConfig,
                object : AssetsOutDestManager(variantName, intermediatesDir) {
                    override fun buildAssetsOutDestFile(): File {
                        return File(streamlineFile, JNI_LIBS)
                    }
                })
            soHandle.singlePerform7z(
                File(streamlineFile, "lib"), null
            )
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