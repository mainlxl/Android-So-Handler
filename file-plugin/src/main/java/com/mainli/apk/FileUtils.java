package com.mainli.apk;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public class FileUtils {
    @NotNull
    public static File join(@NotNull File files, String... paths) {
        File tmp = files;
        for (String path : paths) {
            tmp = new File(tmp, path);
        }
        return tmp;
    }
}
