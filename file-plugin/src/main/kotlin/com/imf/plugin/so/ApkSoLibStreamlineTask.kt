package com.imf.plugin.so

import brut.androlib.Androlib
import brut.androlib.ApkDecoder
import brut.androlib.options.BuildOptions
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.elf.ElfParser
import com.google.gson.Gson
import com.mainli.apk.ApkSign
import com.mainli.apk.ZipUtil
import org.apache.commons.io.FileUtils
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
                val newApk = if (pluginConfig.useApktool) {
                    streamlineApkSoFileByApkTool(apkFile)
                } else {
                    streamlineApkSoFile(apkFile)
                }
                if (newApk?.exists() == true) {
                    val signApk = ApkSign.sign(newApk, variant)
                    newApk.delete()
                    if (pluginConfig.backupApk) {
                        apkFile.renameTo(File(apkFile.parentFile, "backup-" + apkFile.name))
                    } else {
                        apkFile.delete()
                    }
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

    open fun streamlineApkSoFileByApkTool(apk: File?): File? {
        if (apk == null || !apk.exists()) {
            return null
        }
        val outPutApk = File(apk.parentFile, "_streamlineApkBySoFile.apk")
        if (outPutApk.exists()) {
            outPutApk.delete()
        }

        val apkDecoder = ApkDecoder()
        apkDecoder.setApkFile(apk)
        val outDir = File(apk.parentFile.path + File.separator + apk.nameWithoutExtension)
        outDir.delete()
        apkDecoder.setOutDir(outDir)
        apkDecoder.setForceDelete(true)
        apkDecoder.setDecodeSources(ApkDecoder.DECODE_SOURCES_NONE)
        apkDecoder.decode()

        val streamlineDir = File(apk.canonicalFile.parentFile, "apktoolline")
        streamlineDir.delete()
        streamlineDir.mkdirs()

        outDir.listFiles()?.forEach { libDir ->
            if (libDir.isDirectory) {
                if (libDir.name.startsWith("lib")) {
                    handleZipSo(libDir, streamlineDir)
                    handleDeleteSo(libDir, streamlineDir)
                    handleOtherSo(libDir)
                }
            }
        }
        val assetsDir = outDir.resolve("assets${File.separator}jniLibs")
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }
        com.android.utils.FileUtils.writeToFile(
            assetsDir.resolve("info.json"),
            Gson().toJson(record)
        )

        val file = outDir.parentFile.resolve("_" + outDir.name + ".apk")
        Androlib(
            BuildOptions().apply {
                useAapt2 = true
            }
        ).build(outDir, file)
        outDir.delete()
        return file
    }

    private var record: HashMap<String, HashMap<String, HandleSoFileInfo>> = hashMapOf()
    private val cmd =
        "${pluginConfig.exe7zName} a %s %s -t7z -mx=9 -m0=LZMA2 -ms=10m -mf=on -mhc=on -mmt=on -mhcf"

    private fun handleOtherSo(libDir: File) {
        libDir.listFiles()?.forEach { abi ->
            if (abi.isDirectory) {
                if (record[abi.name] == null) {
                    record[abi.name] = hashMapOf()
                }
                abi.listFiles()
                    ?.forEach { aSo ->
                        record[abi.name]?.set(
                            aSo.nameWithoutExtension.substring(3),
                            HandleSoFileInfo(
                                false,
                                null,
                                aSo.getNeededDependencies(),
                                null
                            )
                        )
                    }
            }
        }
    }

    private fun handleZipSo(libDir: File, streamlineDir: File) {
        if (pluginConfig.compressSo2AssetsLibs.isNullOrEmpty()) {
            return
        }
        libDir.listFiles()?.forEach { abi ->
            if (abi.isDirectory) {
                if (record[abi.name] == null) {
                    record[abi.name] = hashMapOf()
                }
                abi.listFiles()
                    ?.filter {
                        pluginConfig.compressSo2AssetsLibs?.contains(it.name) == true &&
                                pluginConfig.excludeDependencies?.contains(it.name) == false
                    }
                    ?.forEach { aSo ->
                        val dependencies = aSo.getNeededDependencies()

                        val md5 = getFileMD5ToString(aSo)

                        val newSo = aSo.parentFile.resolve(md5)
                        aSo.renameTo(newSo)
                        renameList.add(aSo.name)

                        val assetsAbiDir =
                            libDir.parentFile.resolve("assets${File.separator}jniLibs${File.separator}${aSo.parentFile.name}")
                        if (!assetsAbiDir.exists()) {
                            assetsAbiDir.mkdirs()
                        }

                        val key = aSo.nameWithoutExtension.substring(3)
                        val destFile = assetsAbiDir.resolve("$key&$md5.7z")
                        val exeCmd = String.format(cmd, destFile.absolutePath, newSo.absolutePath)
                        Runtime.getRuntime().exec(exeCmd).waitFor()

                        record[abi.name]?.set(
                            aSo.nameWithoutExtension.substring(3),
                            HandleSoFileInfo(
                                true,
                                md5,
                                dependencies,
                                destFile.name
                            )
                        )

                        newSo.delete()
                    }
            }
        }
    }

    private fun handleDeleteSo(libDir: File, streamlineDir: File) {
        if (pluginConfig.deleteSoLibs.isNullOrEmpty()) {
            return
        }

        val backupDeleteSoDir = File(streamlineDir, "backupDeleteSo")
        backupDeleteSoDir.delete()
        libDir.listFiles()?.forEach { abi ->
            if (abi.isDirectory) {
                if (record[abi.name] == null) {
                    record[abi.name] = hashMapOf()
                }
                abi.listFiles()
                    ?.filter {
                        pluginConfig.deleteSoLibs?.contains(it.name) == true &&
                                pluginConfig.excludeDependencies?.contains(it.name) == false
                    }
                    ?.forEach { aSo ->
                        if (pluginConfig.backupDeleteSo) {
                            FileUtils.copyFile(
                                aSo,
                                backupDeleteSoDir.resolve(abi.name).resolve(aSo.name)
                            )
                        }
                        val md5 = getFileMD5ToString(aSo)
                        val url = pluginConfig.onDeleteSo?.invoke(aSo, md5)
                        record[abi.name]?.set(
                            aSo.nameWithoutExtension.substring(3),
                            HandleSoFileInfo(
                                false,
                                md5,
                                aSo.getNeededDependencies(),
                                null,
                                url
                            )
                        )
                        aSo.delete()
                    }
            }
        }
    }

    private val renameList = mutableSetOf<String>();

    private fun File.getNeededDependencies(): List<String>? {
        if (pluginConfig.deleteSoLibs.isNullOrEmpty() && pluginConfig.compressSo2AssetsLibs.isNullOrEmpty()) {
            return null
        }

        var set = mutableSetOf<String>()

        ElfParser(this).use {
            set.addAll(it.parseNeededDependencies())
        }

        // 在不需要全部依赖下, 尝试进行依赖简化
        if (!pluginConfig.neededRetainAllDependencies && set.isNotEmpty()) {
            set = set.filter {
                pluginConfig.deleteSoLibs!!.contains(it) ||
                        pluginConfig.compressSo2AssetsLibs!!.contains(it)
            }.filter {
                renameList.contains(it) || this.parentFile.resolve(it).exists()
            }.toMutableSet()
        }

        // 扩展自定义依赖
        pluginConfig.customDependencies?.get(name)?.let {
            set.addAll(it)
        }

        // libxxx.so -> xxx
        val list = set.filter { pluginConfig.excludeDependencies?.contains(it) == false }
            .map { it.substring(3).substringBefore(".so") }

        return if (set.isEmpty()) null
        else list
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