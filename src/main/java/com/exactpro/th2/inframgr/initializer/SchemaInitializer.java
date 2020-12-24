/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressSpec;
import org.apache.commons.text.RandomStringGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class SchemaInitializer {

    private static final Logger logger = LoggerFactory.getLogger(SchemaInitializer.class);

    private static final String RABBITMQ_CONFIGMAP_PARAM = "rabbitmq";
    private static final String RABBITMQ_EXTERNAL_CONFIGMAP_PARAM = "rabbitmq-ext";
    private static final String CASSANDRA_CONFIGMAP_PARAM = "cassandra";
    private static final String CASSANDRA_EXTERNAL_CONFIGMAP_PARAM = "cassandra-ext";
    private static final String LOGGING_CONFIGMAP_PARAM = "logging";

    private static final String RABBITMQ_JSON_KEY = "rabbitMQ.json";
    private static final String RABBITMQ_JSON_VHOST_KEY = "vHost";
    private static final String RABBITMQ_JSON_USERNAME_KEY = "username";
    private static final String RABBITMQ_SECRET_PASSWORD_KEY = "rabbitmq-password";
    private static final String RABBITMQ_SECRET_USERNAME_KEY = "rabbitmq-username";

    private static final String CASSANDRA_JSON_KEY = "cradle.json";
    private static final String CASSANDRA_JSON_KEYSPACE_KEY = "keyspace";

    private static final String INGRESS_PATH_PATTERN = "${SCHEMA_NAME}";

    public static void ensureSchema(String schemaName, Kubernetes kube) throws Exception {
        ensureNameSpace(schemaName, kube);
    }


    private static void ensureNameSpace(String schemaName, Kubernetes kube) throws IOException {

        Config config = Config.getInstance();
        if (kube.existsNamespace())
            return;

        // namespace not found, create it
        logger.info("Creating namespace \"{}\"", kube.getNamespaceName());
        kube.createNamespace();

        copySecrets(config, kube);

        Map<String, String> configMaps = config.getKubernetes().getConfigMaps();
        copyConfigMap(configMaps, LOGGING_CONFIGMAP_PARAM, kube);

        copyIngress(config, kube);

        ensureRabbitMQResources(config, schemaName, kube);
        ensureKeyspace(config, schemaName,  kube);
    }


    public static void createRabbitMQSecret(Config config, Kubernetes kube, String username) {

        Config.RabbitMQConfig rabbitMQConfig = config.getRabbitMQ();
        String secretName = rabbitMQConfig.getSecret();
        String password = generateRandomPassword(rabbitMQConfig);

        logger.info("Creating \"{}\"", ResourcePath.annotationFor(kube.getNamespaceName(), Kubernetes.KIND_SECRET, secretName));

        Map<String, String> data = new HashMap<>();
        data.put(RABBITMQ_SECRET_PASSWORD_KEY, base64Encode(password));
        data.put(RABBITMQ_SECRET_USERNAME_KEY, base64Encode(username));

        ObjectMeta meta = new ObjectMeta();
        meta.setName(secretName);

        Secret secret = new Secret();
        secret.setApiVersion(Kubernetes.API_VERSION_V1);
        secret.setKind(Kubernetes.KIND_SECRET);
        secret.setType(Kubernetes.SECRET_TYPE_OPAQUE);
        secret.setMetadata(meta);
        secret.setData(data);

        kube.createOrReplaceSecret(secret);
    }


    public static void copyRabbitMQConfigMap(String configMapName, String vHostName, String username, Kubernetes kube) {

        if (configMapName == null || configMapName.isEmpty())
            return;

        String namespace = kube.getNamespaceName();
        ConfigMap cm = kube.currentNamespace().getConfigMap(configMapName);
        if (cm == null || cm.getData() == null || cm.getData().get(RABBITMQ_JSON_KEY) == null)   {
            logger.error("Failed to load ConfigMap \"{}\"", configMapName);
            return;
        }

        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, configMapName);
        Map<String, String> cmData = cm.getData();

        // copy config map with updated vHost value to namespace
        try {
            logger.info("Creating \"{}\"", resourceLabel);

            ObjectMapper mapper = new ObjectMapper();

            var rabbitMQJson = mapper.readValue(cmData.get(RABBITMQ_JSON_KEY), Map.class);
            rabbitMQJson.put(RABBITMQ_JSON_VHOST_KEY, vHostName);
            rabbitMQJson.put(RABBITMQ_JSON_USERNAME_KEY, username);
            cmData.put(RABBITMQ_JSON_KEY, mapper.writeValueAsString(rabbitMQJson));

            kube.createOrReplaceConfigMap(cm);
        } catch (Exception e) {
            logger.error("Exception copying \"{}\"", resourceLabel, e);
        }
    }


    public static void ensureRabbitMQResources(Config config, String schemaName, Kubernetes kube) {

        Map<String, String> configMaps = config.getKubernetes().getConfigMaps();
        Config.RabbitMQConfig rabbitMQConfig = config.getRabbitMQ();

        String vHostName = rabbitMQConfig.getVhostPrefix() + schemaName;
        String username = rabbitMQConfig.getUsernamePrefix() + schemaName;

        // copy config map with updated vHost value to namespace
        try {
            logger.info("Creating RabbitMQ Secret and ConfigMap for schema \"{}\"", schemaName);
            createRabbitMQSecret(config, kube, username);
            copyRabbitMQConfigMap(configMaps.get(RABBITMQ_CONFIGMAP_PARAM), vHostName, username, kube);
            copyRabbitMQConfigMap(configMaps.get(RABBITMQ_EXTERNAL_CONFIGMAP_PARAM), vHostName, username, kube);
        } catch (Exception e) {
            logger.error("Exception writing RabbitMQ configuration resources", e);
        }
    }


    private static void copyCassandraConfigMap(String configMapName, String keyspaceName, Kubernetes kube) {

        if (configMapName == null || configMapName.isEmpty())
            return;

        ConfigMap cm = kube.currentNamespace().getConfigMap(configMapName);
        if (cm == null || cm.getData() == null || cm.getData().get(CASSANDRA_JSON_KEY) == null)   {
            logger.error("Failed to load ConfigMap \"{}\"", configMapName);
            return;
        }

        String namespace = kube.getNamespaceName();
        Map<String, String> cmData = cm.getData();

        // copy config map with updated keyspace name
        try {
            logger.info("Creating \"{}\"", ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, configMapName));

            ObjectMapper mapper = new ObjectMapper();
            var cradleMQJson = mapper.readValue(cmData.get(CASSANDRA_JSON_KEY), Map.class);
            cradleMQJson.put(CASSANDRA_JSON_KEYSPACE_KEY, keyspaceName);
            cmData.put(CASSANDRA_JSON_KEY, mapper.writeValueAsString(cradleMQJson));

            kube.createOrReplaceConfigMap(cm);
        } catch (Exception e) {
            logger.error("Exception creating \"{}\"", ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, configMapName), e);
        }
    }


    public static void ensureKeyspace(Config config, String schemaName, Kubernetes kube) {

        Config.CassandraConfig cassandraConfig = config.getCassandra();
        String keyspaceName = (cassandraConfig.getKeyspacePrefix() + schemaName).replace("-", "_");

        Map<String, String> configMaps = config.getKubernetes().getConfigMaps();
        copyCassandraConfigMap(configMaps.get(CASSANDRA_CONFIGMAP_PARAM), keyspaceName, kube);
        copyCassandraConfigMap(configMaps.get(CASSANDRA_EXTERNAL_CONFIGMAP_PARAM), keyspaceName, kube);
    }


    public static void copyConfigMap(Map<String, String> configMaps, String configMapKey, Kubernetes kube) {


        String configMapName = configMaps.get(configMapKey);
        if (configMapName == null || configMapName.isEmpty()) {
            logger.warn("ConfigMap for \"{}\" not configured, ignoring", configMapKey);
            return;
        }

        String namespace = kube.getNamespaceName();
        ConfigMap cm = kube.currentNamespace().getConfigMap(configMapName);
        if (cm == null || cm.getData() == null)   {
            logger.error("Failed to load ConfigMap \"{}\"", configMapName);
            return;
        }

        // copy config map
        try {
            logger.info("Creating \"{}\"", ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, configMapName));
            kube.createOrReplaceConfigMap(cm);
        } catch (Exception e) {
            logger.error("Exception creating \"{}\"", ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, configMapName), e);
        }
    }

    private static Object changeIngresSpecValues (Object obj, String pattern, String replacement) {
        if (obj instanceof String) {
            return ((String) obj).replaceAll(pattern, replacement);
        }

        if (obj instanceof List) {
            ArrayList<Object> ls = new ArrayList<>();

            for (var el : (List) obj) {
                if (el instanceof Map || el instanceof List) {
                    ls.add(changeIngresSpecValues(el, pattern, replacement));
                }

                if (el instanceof String) {
                    ls.add (((String) el).replaceAll(pattern, replacement));
                }
            }

            return ls;
        }

        if (obj instanceof Map) {
            HashMap<String, Object> hmap = new HashMap<>();

            for (var el : ((Map<String, ?>) obj).entrySet()) {
                var entryKey = el.getKey();
                var entryVal = el.getValue();

                if (entryVal instanceof Map || entryVal instanceof List) {
                    hmap.put(entryKey, changeIngresSpecValues(entryVal, pattern, replacement));
                    continue;
                }

                if (entryVal instanceof String) {
                    if (((String) entryVal).contains(pattern)) {
                        hmap.put(entryKey, (((String) entryVal).replace(pattern, replacement)));
                        continue;
                    }
                }

                hmap.put(entryKey, entryVal);
            }

            return hmap;
        }

        throw new RuntimeException("Exception in parsing");
    }

    private static IngressSpec copyIngressSpec (IngressSpec ingressSpec, String namespace) {

        logger.info("Creating new IngressSpec");

        ObjectMapper mapper = new ObjectMapper();
        try {
            HashMap <String, Object> hmap =
                    mapper.readValue(mapper.writeValueAsString(ingressSpec), HashMap.class);

            HashMap <String, Object> newHmap = new HashMap<>();

            for (var entry : hmap.entrySet()) {
                newHmap.put(entry.getKey(), changeIngresSpecValues(entry.getValue(), INGRESS_PATH_PATTERN, namespace));
            }

            IngressSpec newIngressSpec = mapper.readValue(mapper.writeValueAsString(newHmap), IngressSpec.class);

            return newIngressSpec;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        throw new RuntimeException("Could not deserialize the object");
    }

    private static void copyIngress(Config config, Kubernetes kube) {

        String ingressName = config.getKubernetes().getIngress();
        String namespace = kube.getNamespaceName();
        try {
            logger.info("Creating \"{}\"", ResourcePath.annotationFor(namespace, "Ingress", ingressName));

            Ingress ingress = kube.loadIngress("ingress-rules");

            IngressSpec newIngressSpec = copyIngressSpec(ingress.getSpec(), namespace);
            logger.info(ingress.getSpec().toString());
            logger.info(newIngressSpec.toString());

            ObjectMeta objectMeta = new ObjectMeta();
            objectMeta.setName(ingress.getMetadata().getName());
//            objectMeta.setAnnotations(ingress.getMetadata().getAnnotations());

            Ingress newIngress = new IngressBuilder()
                    .withSpec(newIngressSpec)
                    .withMetadata(objectMeta)
                    .build();

            kube.saveIngress(newIngress, namespace);
        } catch (Exception e) {
            logger.error("Exception creating ingress \"{}\"", ResourcePath.annotationFor(namespace, Kubernetes.KIND_INGRESS, ingressName), e);
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


    private static void copySecrets(Config config, Kubernetes kube) {

        Map<String, Secret> workingNamespaceSecrets = kube.currentNamespace().getSecrets();
        Map<String, Secret> targetNamespaceSecrets = kube.getSecrets();

        String namespace = kube.getNamespaceName();

        Config.RabbitMQConfig rabbitMQConfig = config.getRabbitMQ();
        String rmqSecretName = rabbitMQConfig.getSecret();

        for (String secretName : config.getKubernetes().getSecretNames())
            if (!secretName.equals(rmqSecretName)) {

                Secret secret = workingNamespaceSecrets.get(secretName);
                if (secret == null) {
                    logger.error(
                            "Unable to copy to \"{}\" because secret not found in working namespace",
                             ResourcePath.annotationFor(namespace, Kubernetes.KIND_SECRET, secretName));
                    continue;
                }


                if (targetNamespaceSecrets.containsKey(secretName))
                    logger.debug("Secret \"{}\" already exists, skipping", ResourcePath.annotationFor(namespace, Kubernetes.KIND_SECRET, secretName));
                else
                    try {
                        Secret copy = makeCopy(secret);
                        kube.createOrReplaceSecret(copy);
                        logger.info("Copied \"{}\"", ResourcePath.annotationFor(namespace, Kubernetes.KIND_SECRET, secretName));
                    } catch (Exception e) {
                        logger.error("Exception copying \"{}\"", ResourcePath.annotationFor(namespace, Kubernetes.KIND_SECRET, secretName), e);
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
