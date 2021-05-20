package com.exactpro.th2.inframgr.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static com.exactpro.th2.inframgr.k8s.K8sCustomResource.KEY_SOURCE_HASH;

public class SourceHashUtil {

    public static String digest(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setSourceHash(Map<String, String> annotations, Map<String, String> data) {
        String dataStr = String.join(", ", data.values());
        String keysStr = String.join(",", data.keySet());
        annotations.put(KEY_SOURCE_HASH, digest(dataStr + keysStr));
    }
}
