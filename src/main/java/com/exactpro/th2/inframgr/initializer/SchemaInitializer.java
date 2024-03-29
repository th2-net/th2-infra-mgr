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
import com.exactpro.th2.inframgr.k8s.K8sResourceCache;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.k8s.SchemaRecoveryTask;
import com.exactpro.th2.inframgr.k8s.SecretsManager;
import com.exactpro.th2.inframgr.k8s.cr.ServiceMonitor;
import com.exactpro.th2.inframgr.util.RetryableTaskQueue;
import com.exactpro.th2.inframgr.util.cfg.CassandraConfig;
import com.exactpro.th2.inframgr.util.cfg.RabbitMQConfig;
import com.exactpro.th2.infrarepo.git.Gitter;
import com.exactpro.th2.infrarepo.git.GitterContext;
import com.exactpro.th2.infrarepo.repo.Repository;
import com.exactpro.th2.infrarepo.settings.CradleConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.RandomStringGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.exactpro.th2.inframgr.k8s.Kubernetes.*;
import static com.exactpro.th2.inframgr.statuswatcher.ResourcePath.annotationFor;
import static com.exactpro.th2.inframgr.util.AnnotationUtils.setSourceHash;

public class SchemaInitializer {

    private SchemaInitializer() {
    }

    private static final Logger logger = LoggerFactory.getLogger(SchemaInitializer.class);

    private static final int RECOVERY_THREAD_POOL_SIZE = 1;

    private static final int NAMESPACE_RETRY_DELAY = 10;

    private static final String RABBITMQ_SECRET_NAME_FOR_NAMESPACES = "rabbitmq";

    private static final String CASSANDRA_SECRET_NAME_FOR_NAMESPACES = "cassandra";

    private static final String RABBITMQ_CONFIGMAP_PARAM = "rabbitmq";

    private static final String RABBITMQ_EXTERNAL_CM_PARAM = "rabbitmq-ext";

    private static final String CASSANDRA_CONFIGMAP_PARAM = "cassandra";

    private static final String CASSANDRA_EXT_CONFIGMAP_PARAM = "cassandra-ext";

    public static final String MQ_ROUTER_CM_NAME = "mq-router";

    public static final String GRPC_ROUTER_CM_NAME = "grpc-router";

    public static final String CRADLE_MANAGER_CM_NAME = "cradle-manager";

    public static final String BOOK_CONFIG_CM_NAME = "book-config";

    private static final String RABBITMQ_JSON_KEY = "rabbitMQ.json";

    private static final String RABBITMQ_JSON_VHOST_KEY = "vHost";

    private static final String RABBITMQ_JSON_USERNAME_KEY = "username";

    private static final String RABBITMQ_JSON_EXCHANGE_KEY = "exchangeName";

    private static final String RABBITMQ_SECRET_PASSWORD_KEY = "rabbitmq-password";

    private static final String RABBITMQ_SECRET_USERNAME_KEY = "rabbitmq-username";

    private static final String CRADLE_JSON_KEY = "cradle.json";

    private static final String INSTANCE_LABEL = "app.kubernetes.io/instance";

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new KotlinModule.Builder().build());

    public static final String HELM_ANNOTATION_KEY_PREFIX = "meta.helm.sh/";

    private static final RetryableTaskQueue retryTaskQueue = new RetryableTaskQueue(RECOVERY_THREAD_POOL_SIZE);

    public enum SchemaSyncMode {
        CHECK_NAMESPACE,
        CHECK_RESOURCES,
        FORCE
    }

    public static void ensureSchema(String schemaName, Kubernetes kube) throws Exception {
        ensureSchema(schemaName, kube, Config.getInstance().getKubernetes().getSchemaSyncMode());
        K8sResourceCache.INSTANCE.addNamespace(kube.formatNamespaceName(schemaName));
    }

    public static void ensureSchema(String schemaName, Kubernetes kube, SchemaSyncMode syncMode) throws Exception {
        switch (syncMode) {
            case CHECK_NAMESPACE:
                if (kube.existsNamespace()) {
                    if (!kube.namespaceActive()) {
                        retryTaskQueue.add(new SchemaRecoveryTask(schemaName, NAMESPACE_RETRY_DELAY), true);
                        throw new IllegalStateException(
                                String.format(
                                        "Cannot synchronize branch \"%s\" as corresponding namespace in the wrong state"
                                        , schemaName
                                )
                        );
                    }
                    return;
                }
                ensureNameSpace(schemaName, kube, false);
                break;
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

        // copy Th2BoxConfigurations config maps
        copyConfigMap(kube, MQ_ROUTER_CM_NAME, forceUpdate);
        copyConfigMap(kube, GRPC_ROUTER_CM_NAME, forceUpdate);
        copyConfigMap(kube, CRADLE_MANAGER_CM_NAME, forceUpdate);
        copyConfigMap(kube, BOOK_CONFIG_CM_NAME, forceUpdate);


        // ensure rabbitMq resources
        ensureRabbitMQResources(config, schemaName, kube, forceUpdate);

        //ensure cassandra resources
        copyCassandraSecret(config, kube, forceUpdate);
        ensureCradleConfig(config, schemaName, kube, forceUpdate);

        // copy Service Monitor
        copyServiceMonitor(config, kube, forceUpdate);

        // copy required secrets
        copySecrets(config, kube, forceUpdate);

        // create custom-secrets resource
        ensureCustomSecrets(kube, forceUpdate);
    }

    private static void copyConfigMap(Kubernetes kube, String configMapName, boolean forceUpdate) {
        String newResourceLabel = annotationFor(kube.getNamespaceName(), Kubernetes.KIND_CONFIGMAP, configMapName);
        ConfigMap originalConfigMap = kube.currentNamespace().getConfigMap(configMapName);

        if (originalConfigMap == null || originalConfigMap.getData() == null) {
            logger.error("Failed to load ConfigMap \"{}\" from default namespace", configMapName);
            return;
        }

        if (kube.getConfigMap(configMapName) != null && !forceUpdate) {
            logger.info("\"{}\" already exists, skipping", newResourceLabel);
            return;
        }

        ConfigMap newConfigMap = new ConfigMap();
        newConfigMap.setMetadata(createMetadataWithPreviousAnnotations(
                configMapName,
                newResourceLabel,
                originalConfigMap.getMetadata().getAnnotations())
        );

        newConfigMap.setData(originalConfigMap.getData());
        setSourceHash(newConfigMap);

        kube.createOrReplaceConfigMap(newConfigMap);
        logger.info("Created \"{}\" based on \"{}\" from default namespace", newResourceLabel, configMapName);
    }

    static void ensureRabbitMQResources(Config config, String schemaName, Kubernetes kube, boolean forceUpdate) {

        Map<String, String> configMaps = config.getKubernetes().getConfigMaps();
        String prefix = config.getKubernetes().getNamespacePrefix();
        RabbitMQConfig rabbitMQConfig = config.getRabbitMQ();

        String vHostName = rabbitMQConfig.getVhostName();
        String username = prefix + schemaName;
        String exchange = prefix + schemaName;

        // copy config map with updated vHost value to namespace
        try {
            createRabbitMQSecret(config, kube, username, forceUpdate);
            copyRabbitMQConfigMap(
                    configMaps.get(RABBITMQ_CONFIGMAP_PARAM), vHostName, username, exchange, kube, forceUpdate
            );
            copyRabbitMQConfigMap(
                    configMaps.get(RABBITMQ_EXTERNAL_CM_PARAM), vHostName, username, exchange, kube, forceUpdate
            );
        } catch (Exception e) {
            logger.error("Exception writing RabbitMQ configuration resources", e);
        }
    }

    static void createRabbitMQSecret(Config config, Kubernetes kube, String username, boolean forceUpdate) {

        RabbitMQConfig rabbitMQConfig = config.getRabbitMQ();
        String password = generateRandomPassword(rabbitMQConfig);
        String newResourceLabel = annotationFor(kube.getNamespaceName(),
                Kubernetes.KIND_SECRET, RABBITMQ_SECRET_NAME_FOR_NAMESPACES);

        if (kube.getSecret(RABBITMQ_SECRET_NAME_FOR_NAMESPACES) != null && !forceUpdate) {
            logger.info("\"{}\" already exists, skipping", newResourceLabel);
            return;
        }

        Map<String, String> data = new HashMap<>();
        data.put(RABBITMQ_SECRET_PASSWORD_KEY, base64Encode(password));
        data.put(RABBITMQ_SECRET_USERNAME_KEY, base64Encode(username));

        Secret secret = new Secret();
        secret.setApiVersion(Kubernetes.API_VERSION_V1);
        secret.setKind(Kubernetes.KIND_SECRET);
        secret.setType(Kubernetes.SECRET_TYPE_OPAQUE);
        secret.setMetadata(createMetaDataWithNewAnnotations(RABBITMQ_SECRET_NAME_FOR_NAMESPACES, newResourceLabel));
        secret.setData(data);

        kube.createOrReplaceSecret(secret);
        logger.info("Created \"{}\"", newResourceLabel);

    }

    static void copyRabbitMQConfigMap(String configMapName,
                                      String vHostName,
                                      String username,
                                      String exchange,
                                      Kubernetes kube,
                                      boolean forceUpdate) {

        if (StringUtils.isEmpty(configMapName)) {
            return;
        }

        ConfigMap originalConfigMap = kube.currentNamespace().getConfigMap(configMapName);
        if (configMapNotLoaded(originalConfigMap, RABBITMQ_JSON_KEY)) {
            logger.error("Failed to load ConfigMap \"{}\" from default namespace", configMapName);
            return;
        }

        String newResourceLabel = annotationFor(kube.getNamespaceName(), Kubernetes.KIND_CONFIGMAP, configMapName);

        if (kube.getConfigMap(configMapName) != null && !forceUpdate) {
            logger.info("\"{}\" already exists, skipping", newResourceLabel);
            return;
        }

        // copy config map with updated vHost value to namespace
        try {
            Map<String, String> rabbitMQJson = mapper.readValue(
                    originalConfigMap.getData().get(RABBITMQ_JSON_KEY),
                    new TypeReference<>() {
                    }
            );
            rabbitMQJson.put(RABBITMQ_JSON_VHOST_KEY, vHostName);
            rabbitMQJson.put(RABBITMQ_JSON_USERNAME_KEY, username);
            rabbitMQJson.put(RABBITMQ_JSON_EXCHANGE_KEY, exchange);

            ConfigMap newConfigMap = configMapWithNewData(RABBITMQ_JSON_KEY, rabbitMQJson);

            newConfigMap.setMetadata(createMetadataWithPreviousAnnotations(
                    configMapName,
                    newResourceLabel,
                    originalConfigMap.getMetadata().getAnnotations())
            );
            kube.createOrReplaceConfigMap(newConfigMap);
            logger.info("Created \"{}\" based on \"{}\" from default namespace", newResourceLabel, configMapName);
        } catch (Exception e) {
            logger.error("Exception creating \"{}\"", newResourceLabel, e);
        }
    }

    private static boolean configMapNotLoaded(ConfigMap configMap, String jsonKey) {
        return configMap == null
                || configMap.getData() == null
                || configMap.getData().get(jsonKey) == null;
    }

    private static ConfigMap configMapWithNewData(String jsonKey, Object content)
            throws JsonProcessingException {
        ConfigMap newConfigMap = new ConfigMap();
        Map<String, String> newData = new HashMap<>();
        newData.put(jsonKey, mapper.writeValueAsString(content));
        newConfigMap.setData(newData);
        return newConfigMap;
    }

    static void copyCassandraSecret(Config config, Kubernetes kube, boolean forceUpdate) {

        CassandraConfig cassandraConfig = config.getCassandra();
        String secretName = cassandraConfig.getSecret();
        String newResourceLabel = annotationFor(kube.getNamespaceName(),
                Kubernetes.KIND_SECRET, CASSANDRA_SECRET_NAME_FOR_NAMESPACES);

        if (kube.getSecret(CASSANDRA_SECRET_NAME_FOR_NAMESPACES) != null && !forceUpdate) {
            logger.info("\"{}\" already exists, skipping", newResourceLabel);
            return;
        }

        Secret secret = kube.currentNamespace().getSecrets().get(secretName);
        if (secret == null || secret.getData() == null) {
            logger.error("Failed to load Secret \"{}\" from default namespace", secretName);
            return;
        }
        try {
            Secret newResource = makeSecretCopy(secret);
            newResource.setMetadata(createMetadataWithPreviousAnnotations(
                    CASSANDRA_SECRET_NAME_FOR_NAMESPACES,
                    newResourceLabel,
                    secret.getMetadata().getAnnotations())
            );
            kube.createOrReplaceSecret(newResource);
            logger.info("Created \"{}\" based on \"{}\" from default namespace", newResourceLabel, secretName);
        } catch (Exception e) {
            logger.error("Exception creating \"{}\"", newResourceLabel, e);
        }
    }

    static void ensureCradleConfig(Config config, String schemaName, Kubernetes kube, boolean forceUpdate) {

        try {
            GitterContext ctx = GitterContext.getContext(config.getGit());
            Gitter gitter = ctx.getGitter(schemaName);
            try {
                gitter.lock();
                CradleConfig cradle = Repository.getSettings(gitter).getSpec().getCradle();

                Map<String, String> configMaps = config.getKubernetes().getConfigMaps();
                copyCradleConfigMap(configMaps.get(CASSANDRA_CONFIGMAP_PARAM), cradle, kube, forceUpdate);
                copyCradleConfigMap(configMaps.get(CASSANDRA_EXT_CONFIGMAP_PARAM), cradle, kube, forceUpdate);
            } finally {
                gitter.unlock();
            }
        } catch (Exception e) {
            logger.error("Exception extracting keyspace for \"{}\"", schemaName, e);
        }
    }

    private static void copyCradleConfigMap(String configMapName,
                                            CradleConfig cradle,
                                            Kubernetes kube,
                                            boolean forceUpdate) {

        if (StringUtils.isEmpty(configMapName)) {
            return;
        }
        ConfigMap originalConfigMap = kube.currentNamespace().getConfigMap(configMapName);
        if (configMapNotLoaded(originalConfigMap, CRADLE_JSON_KEY)) {
            logger.error("Failed to load ConfigMap \"{}\" from default namespace", configMapName);
            return;
        }

        String newResourceLabel = annotationFor(kube.getNamespaceName(), Kubernetes.KIND_CONFIGMAP, configMapName);

        if (kube.getConfigMap(configMapName) != null && !forceUpdate) {
            logger.info("\"{}\" already exists, skipping", newResourceLabel);
            return;
        }

        // copy config map with updated keyspace name
        try {
            CradleJsonConfig cradleJsonConfig = mapper.readValue(
                    originalConfigMap.getData().get(CRADLE_JSON_KEY),
                    new TypeReference<>() {
                    }
            );

            cradleJsonConfig.overwriteWith(cradle);

            ConfigMap newConfigMap = configMapWithNewData(CRADLE_JSON_KEY, cradleJsonConfig);
            newConfigMap.setMetadata(createMetadataWithPreviousAnnotations(
                    configMapName,
                    newResourceLabel,
                    originalConfigMap.getMetadata().getAnnotations())
            );
            kube.createOrReplaceConfigMap(newConfigMap);
            logger.info("Created \"{}\" based on \"{}\" from default namespace", newResourceLabel, configMapName);
        } catch (Exception e) {
            logger.error("Exception creating \"{}\"", newResourceLabel, e);
        }
    }

    private static void copyServiceMonitor(Config config, Kubernetes kube, boolean forceUpdate) {
        String serviceMonitorName = config.getKubernetes().getServiceMonitor();
        ServiceMonitor.Type originalServiceMonitor = kube.currentNamespace().loadServiceMonitor(serviceMonitorName);
        if (originalServiceMonitor == null) {
            logger.error("Failed to load ServiceMonitor \"{}\" from default namespace", serviceMonitorName);
            return;
        }
        String namespace = kube.getNamespaceName();
        String newResourceLabel = annotationFor(namespace, KIND_SERVICE_MONITOR, serviceMonitorName);
        try {
            if (kube.loadServiceMonitor(namespace, serviceMonitorName) != null && !forceUpdate) {
                logger.info("\"{}\" already exists, skipping", newResourceLabel);
                return;
            }
            ServiceMonitor.Type newServiceMonitor = new ServiceMonitor.Type();
            newServiceMonitor.setMetadata(originalServiceMonitor.getMetadata());
            newServiceMonitor.getMetadata().setNamespace(namespace);
            processInstanceLabel(newServiceMonitor.getMetadata(), namespace);
            newServiceMonitor.setKind(KIND_SERVICE_MONITOR);
            newServiceMonitor.setSpec(originalServiceMonitor.getSpec());
            kube.createServiceMonitor(newServiceMonitor);
            logger.info("Created \"{}\" based on \"{}\" from default namespace", newResourceLabel, serviceMonitorName);
        } catch (Exception e) {
            logger.error("Exception creating ServiceMonitor \"{}\"", newResourceLabel, e);
        }
    }

    private static void processInstanceLabel(ObjectMeta metadata, String namespace) {
        var labels = metadata.getLabels();
        if (labels.containsKey(INSTANCE_LABEL)) {
            labels.put(INSTANCE_LABEL, namespace);
        }
    }

    private static void copySecrets(Config config, Kubernetes kube, boolean forceUpdate) {

        Map<String, Secret> workingNamespaceSecrets = kube.currentNamespace().getSecrets();
        Map<String, Secret> targetNamespaceSecrets = kube.getSecrets();

        String rmqSecretName = config.getRabbitMQ().getSecret();
        String cassandraSecretName = config.getCassandra().getSecret();

        for (String secretName : config.getKubernetes().getSecretNames()) {
            if (secretName.equals(rmqSecretName) || secretName.equals(cassandraSecretName)) {
                continue;
            }

            Secret originalSecret = workingNamespaceSecrets.get(secretName);
            String newResourceLabel = annotationFor(kube.getNamespaceName(), Kubernetes.KIND_SECRET, secretName);
            if (originalSecret == null || originalSecret.getData() == null) {
                logger.error("Failed to load Secret \"{}\" from default namespace", secretName);
                continue;
            }

            if (targetNamespaceSecrets.containsKey(secretName) && !forceUpdate) {
                logger.info("\"{}\" already exists, skipping", newResourceLabel);
            } else {
                try {
                    Secret newResource = makeSecretCopy(originalSecret);
                    newResource.setMetadata(createMetadataWithPreviousAnnotations(
                            secretName,
                            newResourceLabel,
                            originalSecret.getMetadata().getAnnotations())
                    );
                    kube.createOrReplaceSecret(newResource);
                    logger.info("Created \"{}\" based on \"{}\" from default namespace", newResourceLabel, secretName);
                } catch (Exception e) {
                    logger.error("Exception creating \"{}\"", newResourceLabel, e);
                }
            }
        }

    }

    private static Secret makeSecretCopy(Secret secret) {
        Secret secretCopy = new Secret();
        secretCopy.setKind(secret.getKind());
        secretCopy.setType(secret.getType());
        secretCopy.setApiVersion(secret.getApiVersion());
        secretCopy.setData(secret.getData());
        return secretCopy;
    }

    private static void ensureCustomSecrets(Kubernetes kube, boolean forceUpdate) {
        String secretName = SecretsManager.DEFAULT_SECRET_NAME;
        String newResourceLabel = annotationFor(kube.getNamespaceName(), Kubernetes.KIND_SECRET, secretName);

        if (kube.getSecret(secretName) != null && !forceUpdate) {
            logger.info("\"{}\" already exists, skipping", newResourceLabel);
            return;
        }

        Secret newSecret = new Secret();
        newSecret.setApiVersion(Kubernetes.API_VERSION_V1);
        newSecret.setKind(Kubernetes.KIND_SECRET);
        newSecret.setType(Kubernetes.SECRET_TYPE_OPAQUE);
        newSecret.setMetadata(createMetaDataWithNewAnnotations(secretName, newResourceLabel));
        newSecret.setData(new HashMap<>() {{
            put("cassandraPassword", "");
        }});

        try {
            kube.createOrReplaceSecret(newSecret);
            logger.info("Created \"{}\"", newResourceLabel);
        } catch (Exception e) {
            logger.error("Exception creating \"{}\"", newResourceLabel, e);
        }
    }

    private static String generateRandomPassword(RabbitMQConfig config) {
        RandomStringGenerator pwdGenerator = new RandomStringGenerator.Builder()
                .selectFrom(config.getPasswordChars().toCharArray())
                .build();
        return pwdGenerator.generate(config.getPasswordLength());
    }

    private static String base64Encode(String s) {
        return new String(Base64.getEncoder().encode(s.getBytes()));
    }
}
