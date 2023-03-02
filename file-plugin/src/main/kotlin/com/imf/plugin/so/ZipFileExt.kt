package com.imf.plugin.so

import com.mainli.apk.ZipUtil
import java.io.File
import java.util.zip.ZipFile

/**
 * @author <a href=mailto:mcxinyu@foxmail.com>yuefeng</a> in 2023/3/3.
 */
fun ZipFile.unzipTo(dir: File) = try {
    val entries = entries()
    while (entries.hasMoreElements()) {
        val element = entries.nextElement()
        ZipUtil.unZipFile(this, element, dir)
    }
} finally {
    runCatching { close() }
}