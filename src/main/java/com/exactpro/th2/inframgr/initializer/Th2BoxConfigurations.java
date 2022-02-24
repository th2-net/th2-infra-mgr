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

import static com.exactpro.th2.inframgr.util.SourceHashUtil.setSourceHash;

public class Th2BoxConfigurations {

    private static final Logger logger = LoggerFactory.getLogger(Th2BoxConfigurations.class);

    private static final String MQ_ROUTER_CM_NAME = "mq-router";

    private static final String GRPC_ROUTER_CM_NAME = "grpc-router";

    private static final String CRADLE_MANAGER_CM_NAME = "cradle-manager";

    private static final String MQ_ROUTER_FILE_NAME = "mq_router.json";

    private static final String GRPC_ROUTER_FILE_NAME = "grpc_router.json";

    private static final String CRADLE_MANAGER_FILE_NAME = "cradle_manager.json";

    public static void synchronizeBoxConfigMaps(Map<String, String> mqRouter,
                                                Map<String, String> grpcRouter,
                                                Map<String, String> cradleManager,
                                                Kubernetes kube) throws IOException {
        synchronizeConfigMap(MQ_ROUTER_CM_NAME, MQ_ROUTER_FILE_NAME, mqRouter, kube);
        synchronizeConfigMap(GRPC_ROUTER_CM_NAME, GRPC_ROUTER_FILE_NAME, grpcRouter, kube);
        synchronizeConfigMap(CRADLE_MANAGER_CM_NAME, CRADLE_MANAGER_FILE_NAME, cradleManager, kube);
    }

    private static void synchronizeConfigMap(String configMapName, String fileName, Map<String, String> newData, Kubernetes kube) throws IOException {
        String namespace = kube.getNamespaceName();
        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, configMapName);

        if (newData == null || newData.isEmpty()) {
            logger.debug("Custom configuration for \"{}\" not found. Using default values", resourceLabel);
            return;
        }

        ConfigMap configMap = kube.getConfigMap(configMapName);

        if (configMap == null || configMap.getData() == null) {
            logger.error("Failed to load ConfigMap \"{}\"", configMapName);
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
            setSourceHash(configMap.getMetadata().getAnnotations(), newData);
            kube.createOrReplaceConfigMap(configMap);
        } catch (Exception e) {
            logger.error("Exception Updating \"{}\"", resourceLabel, e);
            throw e;
        }
    }

    private static String getNewDataIfDifferent(String oldData, Map<String, String> newData) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> oldDataMap = objectMapper.readValue(oldData, new TypeReference<>() {});
        String newDataStr = objectMapper.writeValueAsString(newData);
        if(!oldDataMap.equals(newData)){
            return newDataStr;
        }
        return null;
    }
}
