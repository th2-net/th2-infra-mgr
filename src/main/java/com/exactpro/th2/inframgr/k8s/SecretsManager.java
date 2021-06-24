package com.exactpro.th2.inframgr.k8s;

import com.exactpro.th2.inframgr.SecretsController;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SecretsManager {
    private static final Logger logger = LoggerFactory.getLogger(SecretsManager.class);
    public static String DEFAULT_SECRET_NAMESPACE = "schema-secrets";
    public static String DEFAULT_SECRET_NAME = "schema-custom-secrets";
    private final KubernetesClient kubernetesClient = new DefaultKubernetesClient();

    public Secret getCustomSecret() {
        try {
            return kubernetesClient.secrets()
                    .inNamespace(DEFAULT_SECRET_NAMESPACE)
                    .withName(DEFAULT_SECRET_NAME).get();
        } catch (Exception e) {
            logger.error("Exception while getting secrets from \"{}\"", DEFAULT_SECRET_NAMESPACE, e);
            throw e;
        }
    }

    public Set<String> createOrReplaceSecrets(List<SecretsController.SecretsRequestEntry> secretEntries) {
        String resourceLabel = ResourcePath.annotationFor(DEFAULT_SECRET_NAMESPACE, Kubernetes.KIND_SECRET, DEFAULT_SECRET_NAME);
        Set<String> updatedEntries = new HashSet<>();
        Secret secret = getCustomSecret();
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
            kubernetesClient.secrets().inNamespace(DEFAULT_SECRET_NAMESPACE).createOrReplace(secret);
            logger.info("Updated \"{}\"", resourceLabel);
            return updatedEntries;
        } catch (Exception e) {
            logger.error("Exception while updating \"{}\"", resourceLabel, e);
            throw e;
        }
    }

    public Set<String> deleteSecrets(Set<String> secretEntries) {
        String resourceLabel = ResourcePath.annotationFor(DEFAULT_SECRET_NAMESPACE, Kubernetes.KIND_SECRET, DEFAULT_SECRET_NAME);
        Secret secret = getCustomSecret();
        Map<String, String> data = secret.getData();
        if (data == null) {
            data = new HashMap<>();
        }
        for (var secretEntry : secretEntries) {
            data.remove(secretEntry);
        }
        secret.setData(data);
        try {
            kubernetesClient.secrets().inNamespace(DEFAULT_SECRET_NAMESPACE).createOrReplace(secret);
            logger.info("Removed entries from \"{}\"", resourceLabel);
            return secretEntries;
        } catch (Exception e) {
            logger.error("Exception while removing entries \"{}\"", resourceLabel, e);
            throw e;
        }
    }
}
