package com.imf.plugin.so

import com.google.gson.Gson

data class HandleSoFileInfo(
    var saveCompressToAssets: Boolean,
    var md5: String?,
    var dependencies: List<String>? = null,
    var compressName: String? = null,
    var url: String? = null
) {
    //用于生成json
    override fun toString(): String = Gson().toJson(this)
}