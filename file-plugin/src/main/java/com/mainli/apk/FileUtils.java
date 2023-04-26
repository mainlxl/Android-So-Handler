package com.mainli.apk;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

public class FileUtils {
    @NotNull
    public static File join(@NotNull File files, String... paths) {
        File tmp = files;
        for (String path : paths) {
            tmp = new File(tmp, path);
        }
        return tmp;
    }

    public static void copyDir(File srcFile, File descFile) throws Exception {
        // 获取当前源目录下子目录
        File[] files = srcFile.listFiles();
        // 判断当前源目录下是否有目录
        if (files == null) { // 若无，则只建立源目录
            makeDir(srcFile, descFile);
            return;
        } else {// 若有，则判断是否为文件
            // 在目的目录创建源目录
            makeDir(srcFile, descFile);
            // 更新目的目录
            String s;
            if (descFile.getParent() == null) {
                s = descFile.getPath() + srcFile.getName();
            } else {
                s = descFile.getPath() + "/" + srcFile.getName();
            }
            descFile = new File(s);
            // 循环遍历源目录下所有文件或目录
            for (File value : files) {
                // 判断是否为文件
                if (value.isFile()) {// 若是文件，则进行拷贝
                    // 完成文件拷贝
                    Files.copy(value.toPath(), descFile.toPath());
                } else { // 若是目录，则进行递归
                    copyDir(value, descFile);
                }
            }
        }
    }

    private static void makeDir(File srcFile, File descFile) {
        String s;
        // 获取路径字符串
        if (descFile.getParent() == null) {
            s = descFile.getPath() + srcFile.getName();
        } else {
            s = descFile.getPath() + "/" + srcFile.getName();
        }
        File newDir = new File(s);
        if (!newDir.exists()) {
            newDir.mkdirs();
        }
    }
}
