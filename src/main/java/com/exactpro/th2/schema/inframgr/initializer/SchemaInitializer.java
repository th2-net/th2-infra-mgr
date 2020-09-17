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

package com.exactpro.th2.schema.inframgr.initializer;

import com.exactpro.th2.schema.inframgr.Config;
import com.exactpro.th2.schema.inframgr.k8s.Kubernetes;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;

public class SchemaInitializer {

    private static final Logger logger = LoggerFactory.getLogger(SchemaInitializer.class);

    private static final String RABBITMQ_HOST_KEY = "RABBITMQ_HOST";
    private static final String RABBITMQ_PORT_KEY = "RABBITMQ_PORT";
    private static final String RABBITMQ_VHOST_KEY = "RABBITMQ_VHOST";

    public static void ensureSchema(String schemaName, Kubernetes kube) throws Exception {
        ensureNameSpace(schemaName, kube);
    }


    private static void ensureNameSpace(String schemaName, Kubernetes kube) throws IOException {

        Config config = Config.getInstance();
        if (kube.existsNamespace())
            return;

        // namespace not found, create it
        kube.createNamespace();
        copySecrets(config.getKubernetes(), kube);

        ensureVHost(config, schemaName, kube);
    }


    public static void ensureVHost(Config config, String schemaName, Kubernetes kube) {

        String commonConfigMapName = config.getKubernetes().getCommonConfigMap();
        Config.RabbitMQConfig rabbitMQConfig = config.getRabbitMQ();

        ConfigMap cm = kube.currentNamespace().getConfigMap(commonConfigMapName);
        if (cm == null) {
            logger.error("Failed to load common ConfigMap({})", commonConfigMapName);
            return;
        }

        Map<String, String> cmData = cm.getData();
        String host = rabbitMQConfig.getHost() != null ? rabbitMQConfig.getHost() : cmData.get(RABBITMQ_HOST_KEY);

        // TODO: need to sort it out to where to get the management port from !!!
        String port = rabbitMQConfig.getPort() != null ? rabbitMQConfig.getPort() : cmData.get(RABBITMQ_PORT_KEY);

        if (host == null || port == null) {
            logger.error("RabbitMQ server definition is incomplete (host={}, port={})", host, port);
            return;
        }

        String vHostName = rabbitMQConfig.getVhostPrefix() + schemaName;
        String apiUrl = String.format("http://%s:%s/api/vhosts/%s", host, port, vHostName);
        String user = rabbitMQConfig.getUser();
        String pass = rabbitMQConfig.getPassword();

        RestTemplateBuilder builder = new RestTemplateBuilder();
        RestTemplate restTemplate = builder.basicAuthentication(user, pass).build();

        // check if vHost already exists on server
        try {
            try {
                restTemplate.getForObject(apiUrl, RabbitMQvHost.class);
                // no exception at this point means that
                // vHost already exists on server and we do not need to do anything
                logger.info("vHost \"{}\" already exists on server, leaving", vHostName);

            } catch (HttpClientErrorException.NotFound e) {
                // vHost was not found on query, create it
                restTemplate.put(apiUrl, null);
                logger.info("vHost \"{}\" created", vHostName);
            }
        } catch (Exception e) {
            logger.error("Exception creating \"{}\"", vHostName, e);
            return;
        }


        // copy config map with updated vHost value to namespace
        try {
            cmData.put(RABBITMQ_VHOST_KEY, vHostName);
            kube.createOrReplaceConfigMap(cm);
            logger.info("Created ConfigMap \"{}\" in namespace \"{}\"", commonConfigMapName, kube.getNamespaceName());
        } catch (Exception e) {
            logger.error("Exception creating ConfigMap \"{}\" in namespace \"{}\"", commonConfigMapName, kube.getNamespaceName(), e);
        }
    }


    private static void copySecrets(Config.K8sConfig config, Kubernetes kube) {

        Map<String, Secret> workingNamespaceSecrets = kube.currentNamespace().getSecrets();
        Map<String, Secret> targetNamespaceSecrets = kube.getSecrets();

        for (String secretName : config.getSecretNames()) {

            Secret secret = workingNamespaceSecrets.get(secretName);
            if (secret == null) {
                logger.error(
                        "Unable to copy secret \"{}\" to namespace \"{}\" because secret not found in working namespace",
                        secretName, kube.getNamespaceName());
                continue;
            }


            if (targetNamespaceSecrets.containsKey(secretName))
                logger.debug("Skipped secret \"{}\" as it already exists in namespace \"{}\"", secretName, kube.getNamespaceName());
            else
                try {
                    kube.createOrReplaceSecret(secret);
                    logger.info("Secret \"{}\" has been copied to namespace \"{}\"", secretName, kube.getNamespaceName());
                } catch (Exception e) {
                    logger.error(
                            "Exception copying secret \"{}\" to namespace \"{}\"",
                            secretName, kube.getNamespaceName(), e);
                }
        }
    }

}
