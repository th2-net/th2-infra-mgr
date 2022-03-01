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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.schema.CreateKeyspace;
import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.k8s.SecretsManager;
import com.exactpro.th2.inframgr.util.cfg.CassandraConfig;
import com.exactpro.th2.inframgr.util.cfg.RabbitMQConfig;
import com.exactpro.th2.infrarepo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1beta1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1beta1.IngressSpec;
import org.apache.commons.text.RandomStringGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;

import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.createKeyspace;
import static com.exactpro.th2.inframgr.k8s.Kubernetes.createMetaDataWithNewAnnotations;
import static com.exactpro.th2.inframgr.k8s.Kubernetes.createMetadataWithPreviousAnnotations;
import static com.exactpro.th2.inframgr.statuswatcher.ResourcePath.annotationFor;
import static com.exactpro.th2.inframgr.util.AnnotationUtils.setSourceHash;
import static java.util.Collections.unmodifiableMap;

public class SchemaInitializer {

    private static final Logger logger = LoggerFactory.getLogger(SchemaInitializer.class);

    private static final String RABBITMQ_SECRET_NAME_FOR_NAMESPACES = "rabbitmq";

    private static final String CASSANDRA_SECRET_NAME_FOR_NAMESPACES = "cassandra";

    private static final String RABBITMQ_CONFIGMAP_PARAM = "rabbitmq";

    private static final String RABBITMQ_EXTERNAL_CM_PARAM = "rabbitmq-ext";

    private static final String CASSANDRA_CONFIGMAP_PARAM = "cassandra";

    private static final String CASSANDRA_EXT_CONFIGMAP_PARAM = "cassandra-ext";

    public static final String MQ_ROUTER_CM_NAME = "mq-router";

    public static final String GRPC_ROUTER_CM_NAME = "grpc-router";

    public static final String CRADLE_MANAGER_CM_NAME = "cradle-manager";

    private static final String RABBITMQ_JSON_KEY = "rabbitMQ.json";

    private static final String RABBITMQ_JSON_VHOST_KEY = "vHost";

    private static final String RABBITMQ_JSON_USERNAME_KEY = "username";

    private static final String RABBITMQ_SECRET_PASSWORD_KEY = "rabbitmq-password";

    private static final String RABBITMQ_SECRET_USERNAME_KEY = "rabbitmq-username";

    private static final String CASSANDRA_JSON_KEY = "cradle.json";

    private static final String CASSANDRA_JSON_KEYSPACE_KEY = "keyspace";

    private static final String INGRESS_PATH_SUBSTRING = "${SCHEMA_NAMESPACE}";

    private static final ObjectMapper mapper = new ObjectMapper();

    /*
        Ingress annotation values with following key prefix should not be
        passed to new ingress
     */
    public static final String HELM_ANNOTATION_KEY_PREFIX = "meta.helm.sh/";

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
                if (kube.existsNamespace()) {
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

        // ensure rabbitMq resources
        ensureRabbitMQResources(config, schemaName, kube, forceUpdate);

        //ensure cassandra resources
        copyCassandraSecret(config, kube, forceUpdate);
        ensureKeyspace(config, schemaName, kube, forceUpdate);

        // copy Ingress
        copyIngress(config, kube, forceUpdate);

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
        RabbitMQConfig rabbitMQConfig = config.getRabbitMQ();

        String vHostName = rabbitMQConfig.getVhostPrefix() + schemaName;
        String username = rabbitMQConfig.getUsernamePrefix() + schemaName;

        // copy config map with updated vHost value to namespace
        try {
            createRabbitMQSecret(config, kube, username, forceUpdate);
            copyRabbitMQConfigMap(configMaps.get(RABBITMQ_CONFIGMAP_PARAM), vHostName, username, kube, forceUpdate);
            copyRabbitMQConfigMap(configMaps.get(RABBITMQ_EXTERNAL_CM_PARAM), vHostName, username, kube, forceUpdate);
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
                                      Kubernetes kube,
                                      boolean forceUpdate) {

        if (configMapName == null || configMapName.isEmpty()) {
            return;
        }

        ConfigMap originalConfigMap = kube.currentNamespace().getConfigMap(configMapName);
        if (originalConfigMap == null
                || originalConfigMap.getData() == null
                || originalConfigMap.getData().get(RABBITMQ_JSON_KEY) == null) {
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
            var rabbitMQJson = mapper.readValue(originalConfigMap.getData().get(RABBITMQ_JSON_KEY), Map.class);
            rabbitMQJson.put(RABBITMQ_JSON_VHOST_KEY, vHostName);
            rabbitMQJson.put(RABBITMQ_JSON_USERNAME_KEY, username);

            ConfigMap newConfigMap = new ConfigMap();
            Map<String, String> newData = new HashMap<>();
            newData.put(RABBITMQ_JSON_KEY, mapper.writeValueAsString(rabbitMQJson));
            newConfigMap.setMetadata(createMetadataWithPreviousAnnotations(
                    configMapName,
                    newResourceLabel,
                    originalConfigMap.getMetadata().getAnnotations())
            );
            newConfigMap.setData(newData);
            kube.createOrReplaceConfigMap(newConfigMap);
            logger.info("Created \"{}\" based on \"{}\" from default namespace", newResourceLabel, configMapName);
        } catch (Exception e) {
            logger.error("Exception creating \"{}\"", newResourceLabel, e);
        }
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

    static void ensureKeyspace(Config config, String schemaName, Kubernetes kube, boolean forceUpdate) {

        try {
            GitterContext ctx = GitterContext.getContext(config.getGit());
            Gitter gitter = ctx.getGitter(schemaName);
            RepositorySnapshot snapshot;
            try {
                gitter.lock();
                snapshot = Repository.getSnapshot(gitter);
                RepositorySettings repositorySettings = snapshot.getRepositorySettings();

                CassandraConfig cassandraConfig = config.getCassandra();
                var keyspace = repositorySettings.getKeyspace() != null ? repositorySettings.getKeyspace() : schemaName;
                var keyspaceName = (cassandraConfig.getKeyspacePrefix() + keyspace).replace("-", "_");

                Map<String, String> configMaps = config.getKubernetes().getConfigMaps();
                initializeKeyspace(cassandraConfig, keyspaceName);
                copyCassandraConfigMap(configMaps.get(CASSANDRA_CONFIGMAP_PARAM), keyspaceName, kube, forceUpdate);
                copyCassandraConfigMap(configMaps.get(CASSANDRA_EXT_CONFIGMAP_PARAM), keyspaceName, kube, forceUpdate);
            } finally {
                gitter.unlock();
            }
        } catch (Exception e) {
            logger.error("Exception extracting keyspace for \"{}\"", schemaName, e);
        }
    }

    private static void initializeKeyspace(CassandraConfig cassandraConfig, String keyspaceName) {
        CqlSession session;
        try {
            logger.info("Connecting to Cassandra");
            session = CqlSession.builder()
                    .addContactPoint(new InetSocketAddress(cassandraConfig.getHost(), cassandraConfig.getPort()))
                    .withAuthCredentials(cassandraConfig.getUsername(), cassandraConfig.getPassword())
                    .withLocalDatacenter(cassandraConfig.getDataCenter())
                    .build();

        } catch (Exception e) {
            logger.error("Could not open Cassandra connection", e);
            return;
        }

        CreateKeyspace createKs;
        var networkTopologyStrategy = cassandraConfig.getNetworkTopologyStrategy();
        if (networkTopologyStrategy == null) {
            createKs = createKeyspace(keyspaceName)
                    .ifNotExists()
                    .withSimpleStrategy(1);
        } else {
            createKs = createKeyspace(keyspaceName)
                    .ifNotExists()
                    .withNetworkTopologyStrategy(unmodifiableMap(networkTopologyStrategy));
        }

        try {
            logger.info("Initializing keyspace");
            String query = createKs.asCql();
            SimpleStatement statement = SimpleStatement.newInstance(query)
                    .setTimeout(Duration.ofMillis(cassandraConfig.getTimeout()));
            ResultSet rs = session.execute(statement);
            if (rs.wasApplied()) {
                logger.info("Keyspace \"{}\" was created", keyspaceName);
            } else {
                logger.error("Couldn't crete keyspace \"{}\", query \"{}\" was not applied", keyspaceName, query);
            }
        } catch (Exception e) {
            logger.error("Exception while creating keyspace \"{}\"", keyspaceName, e);
        }
    }

    private static void copyCassandraConfigMap(String configMapName,
                                               String keyspaceName,
                                               Kubernetes kube,
                                               boolean forceUpdate) {

        if (configMapName == null || configMapName.isEmpty()) {
            return;
        }
        ConfigMap originalConfigMap = kube.currentNamespace().getConfigMap(configMapName);
        if (originalConfigMap == null
                || originalConfigMap.getData() == null
                || originalConfigMap.getData().get(CASSANDRA_JSON_KEY) == null) {
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
            var cradleMQJson = mapper.readValue(originalConfigMap.getData().get(CASSANDRA_JSON_KEY), Map.class);
            cradleMQJson.put(CASSANDRA_JSON_KEYSPACE_KEY, keyspaceName);
            ConfigMap newConfigMap = new ConfigMap();
            Map<String, String> newData = new HashMap<>();
            newData.put(CASSANDRA_JSON_KEY, mapper.writeValueAsString(cradleMQJson));
            newConfigMap.setMetadata(createMetadataWithPreviousAnnotations(
                    configMapName,
                    newResourceLabel,
                    originalConfigMap.getMetadata().getAnnotations())
            );
            newConfigMap.setData(newData);
            kube.createOrReplaceConfigMap(newConfigMap);
            logger.info("Created \"{}\" based on \"{}\" from default namespace", newResourceLabel, configMapName);
        } catch (Exception e) {
            logger.error("Exception creating \"{}\"", newResourceLabel, e);
        }
    }

    private static void copyIngress(Config config, Kubernetes kube, boolean forceUpdate) {

        String ingressName = config.getKubernetes().getIngress();

        Ingress originalIngress = kube.currentNamespace().getIngress(ingressName);
        if (originalIngress == null) {
            logger.error("Failed to load ingress \"{}\" from default namespace", ingressName);
            return;
        }

        String namespace = kube.getNamespaceName();
        String newResourceLabel = annotationFor(namespace, Kubernetes.KIND_INGRESS, ingressName);
        try {
            if (kube.getIngress(ingressName) != null && !forceUpdate) {
                logger.info("\"{}\" already exists, skipping", newResourceLabel);
                return;
            }

            String spec = mapper.writeValueAsString(originalIngress.getSpec())
                    .replace(INGRESS_PATH_SUBSTRING, namespace);

            IngressSpec newSpec = mapper.readValue(spec, IngressSpec.class);

            Ingress newIngress = new IngressBuilder()
                    .withSpec(newSpec)
                    .withMetadata(createMetadataWithPreviousAnnotations(
                            ingressName,
                            newResourceLabel,
                            originalIngress.getMetadata().getAnnotations()
                            )
                    )
                    .build();

            kube.createOrRepaceIngress(newIngress);
            logger.info("Created \"{}\" based on \"{}\" from default namespace", newResourceLabel, ingressName);
        } catch (Exception e) {
            logger.error("Exception creating ingress \"{}\"", newResourceLabel, e);
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
        newSecret.setData(new HashMap<>());

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
