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

import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.fabric8.kubernetes.api.model.ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static com.exactpro.th2.inframgr.initializer.SchemaInitializer.*;
import static com.exactpro.th2.inframgr.util.AnnotationUtils.stamp;

public class Th2BoxConfigurations {

    private static final Logger logger = LoggerFactory.getLogger(Th2BoxConfigurations.class);

    private static final String MQ_ROUTER_FILE_NAME = "mq_router.json";

    private static final String GRPC_ROUTER_FILE_NAME = "grpc_router.json";

    private static final String CRADLE_MANAGER_FILE_NAME = "cradle_manager.json";

    public static void synchronizeBoxConfigMaps(Map<String, Object> mqRouter,
                                                Map<String, Object> grpcRouter,
                                                Map<String, Object> cradleManager,
                                                String fullCommitRef,
                                                Kubernetes kube) throws IOException {
        synchronizeConfigMap(MQ_ROUTER_CM_NAME, MQ_ROUTER_FILE_NAME, mqRouter, fullCommitRef, kube);
        synchronizeConfigMap(GRPC_ROUTER_CM_NAME, GRPC_ROUTER_FILE_NAME, grpcRouter, fullCommitRef, kube);
        synchronizeConfigMap(CRADLE_MANAGER_CM_NAME, CRADLE_MANAGER_FILE_NAME, cradleManager, fullCommitRef, kube);
    }

    private static void synchronizeConfigMap(String configMapName,
                                             String fileName,
                                             Map<String, Object> newData,
                                             String fullCommitRef,
                                             Kubernetes kube) throws IOException {
        String namespace = kube.getNamespaceName();
        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, configMapName);

        ConfigMap defaultConfigMap = kube.currentNamespace().getConfigMap(configMapName);

        if (defaultConfigMap == null || defaultConfigMap.getData() == null) {
            logger.error("Failed to load ConfigMap \"{}\" from default namespace", configMapName);
            return;
        }

        try {
            Map<String, String> defaultData = defaultConfigMap.getData();
            ConfigMap configMapInSchemaNamespace = kube.getConfigMap(configMapName);
            Map<String, String> dataInSchemaNamespace = configMapInSchemaNamespace.getData();

            if (newData == null || newData.isEmpty()) {
                logger.debug("Custom configuration for \"{}\" not found. Using default values", resourceLabel);
                String defaultDataSection = defaultData.get(fileName);
                if (defaultDataSection.equals(dataInSchemaNamespace.get(fileName))) {
                    logger.info("Config map \"{}\" is up to date", resourceLabel);
                    return;
                }
                dataInSchemaNamespace.put(fileName, defaultDataSection);
                try {
                    logger.info("Resetting to default \"{}\"", resourceLabel);
                    stamp(configMapInSchemaNamespace, fullCommitRef);
                    kube.createOrReplaceConfigMap(configMapInSchemaNamespace);
                } catch (Exception e) {
                    logger.error("Exception Resetting \"{}\"", resourceLabel, e);
                    throw e;
                }
                return;
            }

            String newDataStr = mergeConfigs(defaultData.get(fileName), newData);
            if (newDataStr.equals(dataInSchemaNamespace.get(fileName))) {
                logger.info("Config map \"{}\" is up to date", resourceLabel);
                return;
            }

            try {
                logger.info("Updating \"{}\"", resourceLabel);

                dataInSchemaNamespace.put(fileName, newDataStr);
                stamp(configMapInSchemaNamespace, fullCommitRef);
                kube.createOrReplaceConfigMap(configMapInSchemaNamespace);
            } catch (Exception e) {
                logger.error("Exception Updating \"{}\"", resourceLabel, e);
                throw e;
            }
        } catch (NullPointerException npe) {
            logger.error(npe.getMessage(), npe);
        }
    }

    private static String mergeConfigs(String initialDataStr,
                                       Map<String, Object> newData) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> defaults = objectMapper.readValue(initialDataStr, new TypeReference<>() {
        });
        ObjectReader updater = objectMapper.readerForUpdating(defaults);
        String newDataStr = objectMapper.writeValueAsString(newData);
        return objectMapper.writeValueAsString(updater.readValue(newDataStr));
    }
}
