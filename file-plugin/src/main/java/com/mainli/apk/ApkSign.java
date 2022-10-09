package com.mainli.apk;

import com.android.apksig.ApkSigner;
import com.android.build.gradle.api.ApkVariant;
import com.android.builder.model.SigningConfig;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApkSign {
    public static File sign(File inputApk, ApkVariant variant) {
        return sign(inputApk, variant.getSigningConfig(), variant.getPackageApplicationProvider().get().getMinSdkVersion().get());
    }

    public static File sign(File inputApk, SigningConfig signingConfig, int minSdkVersion) {
        File keystoreFile = signingConfig.getStoreFile();
        String keystorePassworld = signingConfig.getStorePassword();
        String keystoreAlias = signingConfig.getKeyAlias();
        String keystoreAliasPassworld = signingConfig.getKeyPassword();
        File outputApk = new File(inputApk.getParentFile(), "_single.apk");
        String defaultType = KeyStore.getDefaultType();//jks
        try {
            KeyStore ks = KeyStore.getInstance(defaultType);
            loadKeyStoreFromFile(ks, keystoreFile, keystorePassworld);
            if (ks.isKeyEntry(keystoreAlias)) {
                Certificate[] certChain = ks.getCertificateChain(keystoreAlias);

                PrivateKey privateKey = (PrivateKey) ks.getKey(keystoreAlias, keystoreAliasPassworld.toCharArray());
                List<X509Certificate> certs = new ArrayList<>(certChain.length);
                for (Certificate cert : certChain) {
                    certs.add((X509Certificate) cert);
                }
                String v1SigBasename = null;
                String keyFileName = keystoreFile.getName();
                int delimiterIndex = keyFileName.indexOf('.');
                if (delimiterIndex == -1) {
                    v1SigBasename = keyFileName;
                } else {
                    v1SigBasename = keyFileName.substring(0, delimiterIndex);
                }
                ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(v1SigBasename, privateKey, certs).build();


                ApkSigner.Builder apkSignerBuilder = new ApkSigner.Builder(Arrays.asList(signerConfig)).setInputApk(inputApk).setOutputApk(outputApk).setOtherSignersSignaturesPreserved(false).setV1SigningEnabled(signingConfig.isV1SigningEnabled())//
                        .setV2SigningEnabled(signingConfig.isV2SigningEnabled())//
                        .setV3SigningEnabled(signingConfig.isV2SigningEnabled());
                apkSignerBuilder.setMinSdkVersion(minSdkVersion);
                ApkSigner apkSigner = apkSignerBuilder.build();
                try {
                    apkSigner.sign();
                    return outputApk;
                } catch (Exception e) {
                }
            }
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void loadKeyStoreFromFile(KeyStore ks, File file, String passwordStr) throws Exception {
        List<char[]> passwords = Arrays.asList(passwordStr.toCharArray());
        Exception lastFailure = null;
        for (char[] password : passwords) {
            try {
                try (FileInputStream in = new FileInputStream(file)) {
                    ks.load(in, password);
                }
                return;
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        if (lastFailure == null) {
            throw new RuntimeException("No keystore passwords");
        } else {
            throw lastFailure;
        }
    }
}
