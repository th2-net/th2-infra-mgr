/*
 * Copyright 2020-2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.inframgr.k8s;

import com.exactpro.th2.inframgr.SecretsController;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SecretsManager {

    private static final Logger logger = LoggerFactory.getLogger(SecretsManager.class);

    public static final String DEFAULT_SECRET_NAME = "secret-custom-config";

    private final KubernetesClient kubernetesClient = new KubernetesClientBuilder().build();

    private final String prefix;

    public SecretsManager(String prefix) {
        this.prefix = prefix;
    }

    public Secret getCustomSecret(String schemaName) {
        String namespace = prefix + schemaName;
        try {
            return kubernetesClient.secrets()
                    .inNamespace(namespace)
                    .withName(DEFAULT_SECRET_NAME).get();
        } catch (Exception e) {
            logger.error("Exception while getting secrets from \"{}\"", namespace, e);
            throw e;
        }
    }

    public Set<String> createOrReplaceSecrets(String schemaName,
                                              List<SecretsController.SecretsRequestEntry> secretEntries,
                                              String user) {
        String namespace = prefix + schemaName;
        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_SECRET, DEFAULT_SECRET_NAME);
        Set<String> updatedEntries = new HashSet<>();
        Secret secret = getCustomSecret(schemaName);
        Map<String, String> data = secret.getData();
        if (data == null) {
            data = new HashMap<>();
        }
        for (var secretEntry : secretEntries) {
            data.put(secretEntry.getKey(), secretEntry.getData());
            updatedEntries.add(secretEntry.getKey());
        }
        secret.setData(data);
        try {
            kubernetesClient.resource(secret).inNamespace(namespace).serverSideApply();
            logger.info("Updated \"{}\" by user \"{}\"", resourceLabel, user);
            return updatedEntries;
        } catch (Exception e) {
            logger.error("Exception while updating \"{}\"", resourceLabel, e);
            throw e;
        }
    }

    public Set<String> createOrReplaceSecrets(String schemaName,
                                              Map<String, String> secretEntries) {
        String namespace = prefix + schemaName;
        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_SECRET, DEFAULT_SECRET_NAME);
        Secret secret = getCustomSecret(schemaName);
        Map<String, String> data = secret.getData();
        if (data == null) {
            data = new HashMap<>();
        }
        data.putAll(secretEntries);
        secret.setData(data);
        try {
            kubernetesClient.resource(secret).inNamespace(namespace).serverSideApply();
            logger.info("Updated \"{}\"", resourceLabel);
            return secretEntries.keySet();
        } catch (Exception e) {
            logger.error("Exception while updating \"{}\"", resourceLabel, e);
            throw e;
        }
    }

    public Set<String> deleteSecrets(String schemaName, Set<String> secretEntries) {
        String namespace = prefix + schemaName;
        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_SECRET, DEFAULT_SECRET_NAME);
        Secret secret = getCustomSecret(schemaName);
        Map<String, String> data = secret.getData();
        if (data == null) {
            data = new HashMap<>();
        }
        for (var secretEntry : secretEntries) {
            data.remove(secretEntry);
        }
        secret.setData(data);
        try {
            kubernetesClient.resource(secret).inNamespace(namespace).serverSideApply();
            logger.info("Removed entries from \"{}\"", resourceLabel);
            return secretEntries;
        } catch (Exception e) {
            logger.error("Exception while removing entries \"{}\"", resourceLabel, e);
            throw e;
        }
    }
}
