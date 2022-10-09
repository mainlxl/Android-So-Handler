package com.imf.plugin.so

import com.android.utils.FileUtils
import org.gradle.api.Action
import java.io.File

/**
 * 新版本无法通过ExtendedContentType.NATIVE_LIBS获取so处理
 * 手动处理 在merge[Variant]NativeLibs中添加Action处理
 */
class SoFileVariantAction(val variantName: String, val extension: SoFileExtensions, val intermediatesDir: File) : Action<Any?> {

    override fun execute(input: Any?) {
        val soHandle = SoHandle( extension, AssetsOutDestManager(variantName, intermediatesDir))
        val mergedNativeLibsFile = buildMergedNativeLibsFile(variantName)
        soHandle.singlePerform7z(mergedNativeLibsFile, null)
    }

    //debug -> intermediates\merged_native_libs\debug\out\lib
    public fun buildMergedNativeLibsFile(variantName: String): File {
        return FileUtils.join(intermediatesDir, "merged_native_libs", variantName, "out", "lib")
    }

}