package com.mainli.apk;


import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipUtil {
    private static final int BUFFERSIZE = 1024;

    public static File appendFile(File apk, String prefix, File file) {
        if (apk == null || !apk.exists() || file == null || !file.exists()) {
            return null;
        }
        File outPutApk = new File(apk.getParentFile(), "_appendFileUnsigned.apk");
        ZipFile inputZip = null;
        FileOutputStream fileOutputStream = null;
        ZipOutputStream zipOutputStream = null;
        try {
            inputZip = new ZipFile(apk);
            Enumeration<? extends ZipEntry> entries = inputZip.entries();
            //创建压缩包
            fileOutputStream = new FileOutputStream(outPutApk);
            zipOutputStream = new ZipOutputStream(fileOutputStream);
            while (entries.hasMoreElements()) {
                addZipFile2ZipOutputStream(inputZip, entries.nextElement(), zipOutputStream);
            }
            StringBuilder builder = new StringBuilder(prefix);
            if (builder.charAt(builder.length() - 1) == '/') {
                builder.deleteCharAt(builder.length() - 1);
            }
            builder.append('/').append(file.getName());
            if (file.isDirectory()) {
                builder.append('/');
            }
            addFile2ZipOutputStream(file, builder.toString(), zipOutputStream);
            zipOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            outPutApk = null;
        } finally {
            close(inputZip);
            close(zipOutputStream);
        }
        return outPutApk;
    }

    public static void addZipFile2ZipOutputStream(ZipFile inputZip, ZipEntry sourceZipEntry, ZipOutputStream zipOutputStream) {
        InputStream is = null;
        try {
            is = inputZip.getInputStream(sourceZipEntry);
            int len = 0;
            zipOutputStream.putNextEntry(new ZipEntry(sourceZipEntry.getName()));
            byte[] bufer = new byte[BUFFERSIZE];
            while ((len = is.read(bufer)) > 0) {
                zipOutputStream.write(bufer, 0, len);
            }
            zipOutputStream.flush();
            zipOutputStream.closeEntry();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(is);
        }
    }

    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
            }
        }
    }

    public static void addFile2ZipOutputStream(File file, String relativePath, ZipOutputStream zos) {
        InputStream is = null;
        try {
            if (!file.exists()) {
                return;
            }
            if (!file.isDirectory()) {
                ZipEntry zp = new ZipEntry(relativePath);
                zos.putNextEntry(zp);
                is = new FileInputStream(file);
                byte[] buffer = new byte[BUFFERSIZE];
                int length = 0;
                while ((length = is.read(buffer)) >= 0) {
                    zos.write(buffer, 0, length);
                }
                zos.flush();
                zos.closeEntry();
            } else {
                String tempPath = null;
                for (File f : file.listFiles()) {
                    tempPath = relativePath + f.getName();
                    if (f.isDirectory()) {
                        tempPath += "/";
                    }
                    addFile2ZipOutputStream(f, tempPath, zos);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(is);
        }
    }

    public static File unZipFile(ZipFile inputZip, ZipEntry sourceZipEntry, File dir) {
        if (inputZip == null || sourceZipEntry == null || dir == null) {
            return null;
        }
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = inputZip.getInputStream(sourceZipEntry);
            int len = 0;
            File file = new File(dir, sourceZipEntry.getName());
            File parentFile = file.getParentFile();
            if (!parentFile.exists()) {
                parentFile.mkdirs();
            }
            outputStream = new FileOutputStream(file);
            byte[] bufer = new byte[BUFFERSIZE];
            while ((len = inputStream.read(bufer)) > 0) {
                outputStream.write(bufer, 0, len);
            }
            outputStream.flush();
            return file;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(outputStream);
            close(inputStream);
        }
        return null;
    }
}
