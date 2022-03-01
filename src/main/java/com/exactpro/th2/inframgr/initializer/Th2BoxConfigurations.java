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

    public static void synchronizeBoxConfigMaps(Map<String, String> mqRouter,
                                                Map<String, String> grpcRouter,
                                                Map<String, String> cradleManager,
                                                String fullCommitRef,
                                                Kubernetes kube) throws IOException {
        synchronizeConfigMap(MQ_ROUTER_CM_NAME, MQ_ROUTER_FILE_NAME, mqRouter, fullCommitRef, kube);
        synchronizeConfigMap(GRPC_ROUTER_CM_NAME, GRPC_ROUTER_FILE_NAME, grpcRouter, fullCommitRef, kube);
        synchronizeConfigMap(CRADLE_MANAGER_CM_NAME, CRADLE_MANAGER_FILE_NAME, cradleManager, fullCommitRef, kube);
    }

    private static void synchronizeConfigMap(String configMapName,
                                             String fileName,
                                             Map<String, String> newData,
                                             String fullCommitRef,
                                             Kubernetes kube) throws IOException {
        String namespace = kube.getNamespaceName();
        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, configMapName);

        if (newData == null || newData.isEmpty()) {
            logger.debug("Custom configuration for \"{}\" not found. Using default values", resourceLabel);
            return;
        }

        ConfigMap configMap = kube.getConfigMap(configMapName);

        if (configMap == null || configMap.getData() == null) {
            logger.error("Failed to load ConfigMap \"{}\" from default namespace", configMapName);
            return;
        }

        Map<String, String> data = configMap.getData();
        String newDataStr = getNewDataIfDifferent(data.get(fileName), newData);
        if (newDataStr == null) {
            logger.info("Config map \"{}\" is up to date", resourceLabel);
            return;
        }

        try {
            logger.info("Updating \"{}\"", resourceLabel);

            data.put(fileName, newDataStr);
            stamp(configMap, fullCommitRef);
            kube.createOrReplaceConfigMap(configMap);
        } catch (Exception e) {
            logger.error("Exception Updating \"{}\"", resourceLabel, e);
            throw e;
        }
    }

    private static String getNewDataIfDifferent(String oldData,
                                                Map<String, String> newData) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> oldDataMap = objectMapper.readValue(oldData, new TypeReference<>() {
        });
        String newDataStr = objectMapper.writeValueAsString(newData);
        if (!oldDataMap.equals(newData)) {
            return newDataStr;
        }
        return null;
    }
}
