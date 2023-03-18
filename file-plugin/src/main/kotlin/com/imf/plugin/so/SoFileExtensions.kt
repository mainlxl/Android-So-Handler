package com.imf.plugin.so

import java.io.File

/**
 * 必须 open 否则 project.extensions.create 无法创建 SoFileExtensions 的代理子类
 */
open class SoFileExtensions {
    /**
     * 是否开启插件
     */
    var enable: Boolean? = null
        get() {
            if (field == null) {
                return !(deleteSoLibs.isNullOrEmpty() && compressSo2AssetsLibs.isNullOrEmpty())
            }
            return field
        }

    var useApktool: Boolean = false

    /**
     * 7z
     */
    var exe7zName: String = ""
    var abiFilters: Set<String>? = null

    /**
     * 要移除的 so 库
     */
    var deleteSoLibs: Set<String>? = null

    /**
     * 备份删除的 so
     */
    var backupDeleteSo: Boolean = true

    /**
     * 是否备份原来的 apk
     */
    var backupApk: Boolean = false

    /**
     * 删除 so 的回调， File 是 so 文件，String 是 md5
     */
    var onDeleteSo: ((File, String) -> String)? = null

    /**
     * 压缩放在 assets 下的 so 库
     */
    var compressSo2AssetsLibs: Set<String>? = null
    var excludeBuildTypes: Set<String>? = null

    var excludeDependencies: Set<String>? = null


    /**
     * 是否需要保留所有依赖项 默认为保留所有只保留删除或者压缩的依赖 minSdkVersion 小于 23 则需要保留 如果 minSdkVersion 大于 23 则不需要 不可手动设置
     */
    var neededRetainAllDependencies: Boolean = true

    /**
     * 强制保留所有依赖 对于 minSdkVersion 大于 23 的工程也保留所有依赖
     */
    var forceNeededRetainAllDependencies: Boolean? = null

    /**
     * 配置自定义依赖 用于解决 a.so 并未声明依赖 b.so 并且内部通过 dlopen 打开 b.so 或者反射 System.loadLibrary 等跳过 hook 加载 so
     * 库等场景
     */
    var customDependencies: Map<String, List<String>>? = null
}