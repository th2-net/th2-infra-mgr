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

package com.exactpro.th2.inframgr.initializer;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import com.exactpro.th2.infrarepo.Gitter;
import com.exactpro.th2.infrarepo.GitterContext;
import com.exactpro.th2.infrarepo.Repository;
import com.exactpro.th2.infrarepo.RepositorySettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1beta1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1beta1.IngressSpec;
import org.apache.commons.text.RandomStringGenerator;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class SchemaInitializer {

    private static final Logger logger = LoggerFactory.getLogger(SchemaInitializer.class);

    private static final String RABBITMQ_CONFIGMAP_PARAM = "rabbitmq";
    private static final String RABBITMQ_EXTERNAL_CONFIGMAP_PARAM = "rabbitmq-ext";
    private static final String CASSANDRA_CONFIGMAP_PARAM = "cassandra";
    private static final String CASSANDRA_EXTERNAL_CONFIGMAP_PARAM = "cassandra-ext";
    private static final String JAVA_LOGGING_CONFIGMAP_PARAM = "java-logging";
    private static final String LOGGING_CONFIGMAP_PARAM = "logging";

    private static final String RABBITMQ_JSON_KEY = "rabbitMQ.json";
    private static final String RABBITMQ_JSON_VHOST_KEY = "vHost";
    private static final String RABBITMQ_JSON_USERNAME_KEY = "username";
    private static final String RABBITMQ_SECRET_PASSWORD_KEY = "rabbitmq-password";
    private static final String RABBITMQ_SECRET_USERNAME_KEY = "rabbitmq-username";

    private static final String CASSANDRA_JSON_KEY = "cradle.json";
    private static final String CASSANDRA_JSON_KEYSPACE_KEY = "keyspace";

    private static final String LOGGING_CXX_KEY = "cxx.conf";
    private static final String LOGGING_JAVA_KEY = "log4j.properties";
    private static final String LOGGING_PYTHON_KEY = "python.properties";

    private static final String INGRESS_PATH_SUBSTRING = "${SCHEMA_NAMESPACE}";

    private static final String LOGGING_CXX_PATH_SUBSTRING = "${LOGLEVEL_CXX}";
    private static final String LOGGING_JAVA_PATH_SUBSTRING = "${LOGLEVEL_JAVA}";
    private static final String LOGGING_PYTHON_PATH_SUBSTRING = "${LOGLEVEL_PYTHON}";

    /*
        Ingress annotation values with following key prefixes should be
        passed to new ingress without any changes
     */
    private static final String[] INGRESS_KEEP_ANNOTATION_KEY_PREFIXES = {
            "kubernetes.io/",
            "nginx.ingress.kubernetes.io/"
    };

    public enum SchemaSyncMode {
        CHECK_NAMESPACE,
        CHECK_RESOURCES,
        FORCE
    }

    public static void ensureSchema(String schemaName, Kubernetes kube) throws Exception {
        ensureSchema(schemaName, kube, SchemaSyncMode.CHECK_NAMESPACE);
    }

    public static void ensureSchema(String schemaName, Kubernetes kube, SchemaSyncMode syncMode) throws Exception {
        switch (syncMode) {
            case CHECK_NAMESPACE:
                if (kube.existsNamespace())
                    return;
            case CHECK_RESOURCES:
                ensureNameSpace(schemaName, kube, false);
                break;
            case FORCE:
                ensureNameSpace(schemaName, kube, true);
                break;
        }
    }


    private static void ensureNameSpace(String schemaName, Kubernetes kube, boolean forceUpdate) throws IOException {

        Config config = Config.getInstance();

        if (!kube.existsNamespace()) {
            // namespace not found, create it
            logger.info("Creating namespace \"{}\"", kube.getNamespaceName());
            kube.createNamespace();
        }

        copySecrets(config, kube, forceUpdate);

        Map<String, String> configMaps = config.getKubernetes().getConfigMaps();
        copyConfigMap(configMaps, LOGGING_CONFIGMAP_PARAM, kube, forceUpdate);

        copyIngress(config, kube, forceUpdate);

        ensureRabbitMQResources(config, schemaName, kube, forceUpdate);
        ensureKeyspace(config, schemaName, kube, forceUpdate);
    }

    static void createRabbitMQSecret(Config config, Kubernetes kube, String username, boolean forceUpdate) {

        Config.RabbitMQConfig rabbitMQConfig = config.getRabbitMQ();
        String secretName = rabbitMQConfig.getSecret();
        String password = generateRandomPassword(rabbitMQConfig);
        String resourceLabel = ResourcePath.annotationFor(kube.getNamespaceName(), Kubernetes.KIND_SECRET, secretName);

        if (kube.getSecret(secretName) != null && !forceUpdate) {
            return;
        }

        logger.info("Creating \"{}\"", resourceLabel);

        Map<String, String> data = new HashMap<>();
        data.put(RABBITMQ_SECRET_PASSWORD_KEY, base64Encode(password));
        data.put(RABBITMQ_SECRET_USERNAME_KEY, base64Encode(username));

        Secret secret = new Secret();
        secret.setApiVersion(Kubernetes.API_VERSION_V1);
        secret.setKind(Kubernetes.KIND_SECRET);
        secret.setType(Kubernetes.SECRET_TYPE_OPAQUE);
        secret.setMetadata(Kubernetes.createMetadataWithAnnotation(secretName, resourceLabel));
        secret.setData(data);

        kube.createOrReplaceSecret(secret);
    }

    static void copyRabbitMQConfigMap(String configMapName, String vHostName, String username, Kubernetes kube, boolean forceUpdate) {

        if (configMapName == null || configMapName.isEmpty())
            return;

        String namespace = kube.getNamespaceName();
        ConfigMap cm = kube.currentNamespace().getConfigMap(configMapName);
        if (cm == null || cm.getData() == null || cm.getData().get(RABBITMQ_JSON_KEY) == null) {
            logger.error("Failed to load ConfigMap \"{}\"", configMapName);
            return;
        }

        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, configMapName);
        Map<String, String> cmData = cm.getData();

        if (kube.getConfigMap(cm.getMetadata().getName()) != null && !forceUpdate) {
            return;
        }

        // copy config map with updated vHost value to namespace
        try {
            logger.info("Creating \"{}\"", resourceLabel);

            ObjectMapper mapper = new ObjectMapper();

            var rabbitMQJson = mapper.readValue(cmData.get(RABBITMQ_JSON_KEY), Map.class);
            rabbitMQJson.put(RABBITMQ_JSON_VHOST_KEY, vHostName);
            rabbitMQJson.put(RABBITMQ_JSON_USERNAME_KEY, username);
            cmData.put(RABBITMQ_JSON_KEY, mapper.writeValueAsString(rabbitMQJson));
            cm.setMetadata(Kubernetes.createMetadataWithAnnotation(configMapName, resourceLabel));

            kube.createOrReplaceConfigMap(cm);
        } catch (Exception e) {
            logger.error("Exception copying \"{}\"", resourceLabel, e);
        }
    }

    static void ensureRabbitMQResources(Config config, String schemaName, Kubernetes kube, boolean forceUpdate) {

        Map<String, String> configMaps = config.getKubernetes().getConfigMaps();
        Config.RabbitMQConfig rabbitMQConfig = config.getRabbitMQ();

        String vHostName = rabbitMQConfig.getVhostPrefix() + schemaName;
        String username = rabbitMQConfig.getUsernamePrefix() + schemaName;

        // copy config map with updated vHost value to namespace
        try {
            logger.info("Creating RabbitMQ Secret and ConfigMap for schema \"{}\"", schemaName);
            createRabbitMQSecret(config, kube, username, forceUpdate);
            copyRabbitMQConfigMap(configMaps.get(RABBITMQ_CONFIGMAP_PARAM), vHostName, username, kube, forceUpdate);
            copyRabbitMQConfigMap(configMaps.get(RABBITMQ_EXTERNAL_CONFIGMAP_PARAM), vHostName, username, kube, forceUpdate);
        } catch (Exception e) {
            logger.error("Exception writing RabbitMQ configuration resources", e);
        }
    }

    private static void copyCassandraConfigMap(String configMapName, String keyspaceName, Kubernetes kube, boolean forceUpdate) {

        if (configMapName == null || configMapName.isEmpty())
            return;

        ConfigMap cm = kube.currentNamespace().getConfigMap(configMapName);
        if (cm == null || cm.getData() == null || cm.getData().get(CASSANDRA_JSON_KEY) == null) {
            logger.error("Failed to load ConfigMap \"{}\"", configMapName);
            return;
        }

        String namespace = kube.getNamespaceName();
        Map<String, String> cmData = cm.getData();

        if (kube.getConfigMap(configMapName) != null && !forceUpdate) {
            return;
        }

        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, configMapName);

        // copy config map with updated keyspace name
        try {
            logger.info("Creating \"{}\"", resourceLabel);

            ObjectMapper mapper = new ObjectMapper();
            var cradleMQJson = mapper.readValue(cmData.get(CASSANDRA_JSON_KEY), Map.class);
            cradleMQJson.put(CASSANDRA_JSON_KEYSPACE_KEY, keyspaceName);
            cmData.put(CASSANDRA_JSON_KEY, mapper.writeValueAsString(cradleMQJson));
            cm.setMetadata(Kubernetes.createMetadataWithAnnotation(configMapName, resourceLabel));

            kube.createOrReplaceConfigMap(cm);
        } catch (Exception e) {
            logger.error("Exception creating \"{}\"", resourceLabel, e);
        }
    }

    static void ensureKeyspace(Config config, String schemaName, Kubernetes kube, boolean forceUpdate) {

        Config.CassandraConfig cassandraConfig = config.getCassandra();
        String keyspaceName = (cassandraConfig.getKeyspacePrefix() + schemaName).replace("-", "_");

        Map<String, String> configMaps = config.getKubernetes().getConfigMaps();
        copyCassandraConfigMap(configMaps.get(CASSANDRA_CONFIGMAP_PARAM), keyspaceName, kube, forceUpdate);
        copyCassandraConfigMap(configMaps.get(CASSANDRA_EXTERNAL_CONFIGMAP_PARAM), keyspaceName, kube, forceUpdate);
    }

    static void copyConfigMap(Map<String, String> configMaps, String configMapKey, Kubernetes kube, boolean force) {

        String configMapName = configMaps.get(configMapKey);
        if (configMapName == null || configMapName.isEmpty()) {
            logger.warn("ConfigMap for \"{}\" not configured, ignoring", configMapKey);
            return;
        }

        String namespace = kube.getNamespaceName();
        ConfigMap cm = kube.currentNamespace().getConfigMap(configMapName);
        if (cm == null || cm.getData() == null) {
            logger.error("Failed to load ConfigMap \"{}\"", configMapName);
            return;
        }

        if (kube.getConfigMap(configMapName) != null && !force) {
            return;
        }

        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, configMapName);

        // copy config map
        try {
            logger.info("Creating \"{}\"", resourceLabel);
            cm.setMetadata(Kubernetes.createMetadataWithAnnotation(configMapName, resourceLabel));
            kube.createOrReplaceConfigMap(cm);
        } catch (Exception e) {
            logger.error("Exception creating \"{}\"", resourceLabel, e);
        }
    }


    private static void copyIngress(Config config, Kubernetes kube, boolean forceUpdate) {

        String ingressName = config.getKubernetes().getIngress();
        String namespace = kube.getNamespaceName();
        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_INGRESS, ingressName);
        try {
            Ingress ingress = kube.currentNamespace().getIngress(ingressName);

            if (kube.getIngress(ingressName) != null && !forceUpdate) {
                return;
            }

            logger.info("Creating \"{}\"", resourceLabel);

            ObjectMapper mapper = new ObjectMapper();
            String spec = mapper.writeValueAsString(ingress.getSpec()).replace(INGRESS_PATH_SUBSTRING, namespace);
            IngressSpec newSpec = mapper.readValue(spec, IngressSpec.class);

            ObjectMeta meta = Kubernetes.createMetadataWithAnnotation(ingressName, resourceLabel);

            Map<String, String> oldAnnotations = ingress.getMetadata().getAnnotations();
            Map<String, String> newAnnotations = meta.getAnnotations();
            for (var entry : oldAnnotations.entrySet()) {
                if (entry.getKey() == null)
                    continue;

                if (Arrays.stream(INGRESS_KEEP_ANNOTATION_KEY_PREFIXES)
                        .anyMatch(prefix -> entry.getKey().startsWith(prefix)))
                    newAnnotations.put(entry.getKey(), entry.getValue());
            }

            Ingress newIngress = new IngressBuilder()
                    .withSpec(newSpec)
                    .withMetadata(meta)
                    .build();

            kube.createOrRepaceIngress(newIngress);
        } catch (Exception e) {
            logger.error("Exception creating ingress \"{}\"", resourceLabel, e);
        }
    }

    private static Secret makeCopy(Secret secret) {
        Secret secretCopy = new Secret();
        secretCopy.setKind(secret.getKind());
        secretCopy.setType(secret.getType());
        secretCopy.setApiVersion(secret.getApiVersion());
        secretCopy.setMetadata(new ObjectMeta());
        secretCopy.getMetadata().setName(secret.getMetadata().getName());
        secretCopy.setData(secret.getData());
        return secretCopy;
    }

    private static void copySecrets(Config config, Kubernetes kube, boolean forceUpdate) {

        Map<String, Secret> workingNamespaceSecrets = kube.currentNamespace().getSecrets();
        Map<String, Secret> targetNamespaceSecrets = kube.getSecrets();

        String namespace = kube.getNamespaceName();

        Config.RabbitMQConfig rabbitMQConfig = config.getRabbitMQ();
        String rmqSecretName = rabbitMQConfig.getSecret();

        for (String secretName : config.getKubernetes().getSecretNames())
            if (!secretName.equals(rmqSecretName)) {

                Secret secret = workingNamespaceSecrets.get(secretName);
                String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_SECRET, secretName);
                if (secret == null) {
                    logger.error(
                            "Unable to copy to \"{}\" because secret not found in working namespace",
                            resourceLabel);
                    continue;
                }

                if (targetNamespaceSecrets.containsKey(secretName) && !forceUpdate)
                    logger.info("Secret \"{}\" already exists, skipping", resourceLabel);
                else
                    try {
                        Secret copy = makeCopy(secret);
                        copy.setMetadata(Kubernetes.createMetadataWithAnnotation(secretName, resourceLabel));
                        kube.createOrReplaceSecret(copy);
                        logger.info("Copied \"{}\"", resourceLabel);
                    } catch (Exception e) {
                        logger.error("Exception copying \"{}\"", resourceLabel, e);
                    }
            }
    }

    private static String generateRandomPassword(Config.RabbitMQConfig config) {
        RandomStringGenerator pwdGenerator = new RandomStringGenerator.Builder()
                .selectFrom(config.getPasswordChars().toCharArray())
                .build();
        return pwdGenerator.generate(config.getPasswordLength());
    }

    private static String base64Encode(String s) {
        return new String(Base64.getEncoder().encode(s.getBytes()));
    }
}
