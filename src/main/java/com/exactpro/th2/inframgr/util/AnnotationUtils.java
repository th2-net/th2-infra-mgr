/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.inframgr.util;

import io.fabric8.kubernetes.api.model.ConfigMap;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static com.exactpro.th2.inframgr.k8s.K8sCustomResource.KEY_COMMIT_HASH;
import static com.exactpro.th2.inframgr.k8s.K8sCustomResource.KEY_SOURCE_HASH;

public class AnnotationUtils {

    private AnnotationUtils() {
        throw new AssertionError();
    }

    public static void stamp(ConfigMap cm, String fullCommitHash) {
        setSourceHash(cm);
        setCommitHash(cm, fullCommitHash);
    }

    public static void setSourceHash(ConfigMap cm) {
        Map<String, String> annotations = cm.getMetadata().getAnnotations();
        Map<String, String> data = cm.getData();

        String dataStr = String.join(", ", data.values());
        String keysStr = String.join(",", data.keySet());
        annotations.put(KEY_SOURCE_HASH, digest(dataStr + keysStr));
    }

    private static void setCommitHash(ConfigMap cm, String fullCommitHash) {
        Map<String, String> annotations = cm.getMetadata().getAnnotations();
        annotations.put(KEY_COMMIT_HASH, fullCommitHash);
    }

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
}
