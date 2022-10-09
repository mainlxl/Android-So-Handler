package com.mainli.mylibrary;

/* loaded from: aar-release.aar:classes.jar:com/mainli/mylibrary/NativeLibTest.class */
public class NativeLibTest {
    public static native String stringFromJNI();

    static {
        System.loadLibrary("native-aar-lib");
    }
}