package com.imf.so;

/**
 * @Author: lixiaoliang
 * @Date: 2021/8/13 3:50 PM
 * @Description: so库加载代理
 */
public class SoLoadHook {
    private static volatile SoLoadProxy sSoLoadProxy = new DefaultSoLoadProxy();

    public static void setSoLoadProxy(SoLoadProxy soLoadProxy) {
        if (soLoadProxy == null) {
            return;
        }
        sSoLoadProxy = soLoadProxy;
    }

    public static void loadLibrary(String libName) {
        sSoLoadProxy.loadLibrary(libName);
    }

    public static void load(String filename) {
        sSoLoadProxy.load(filename);
    }
}