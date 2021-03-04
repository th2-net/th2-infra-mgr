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
import com.exactpro.th2.infrarepo.RepositoryResource;
import io.fabric8.kubernetes.api.model.ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class LoggingConfigMap {

    private static final Logger logger = LoggerFactory.getLogger(LoggingConfigMap.class);

    private static final String LOGGING_CONFIGMAP_PARAM = "logging";

    private static final String LOGGING_JSON_KEY = "logLevel";

    private static final String LOGGING_CXX_PATH_SUBSTRING = "${LOGLEVEL_CXX}";
    private static final String LOGGING_JAVA_PATH_SUBSTRING = "${LOGLEVEL_JAVA}";
    private static final String LOGGING_PYTHON_PATH_SUBSTRING = "${LOGLEVEL_PYTHON}";

    public static void checkLoggingConfigMap(RepositoryResource resource, String logLevel, Kubernetes kube) throws IOException {
        if (resource.getMetadata() != null && resource.getMetadata().getName().equals(getLoggingConfigMapName())) {
            copyLoggingConfigMap(logLevel, kube, true);
        }
    }

    public static void copyLoggingConfigMap(String logLevel, Kubernetes kube) throws IOException {
        copyLoggingConfigMap(logLevel, kube, false);
    }

    public static void copyLoggingConfigMap(String logLevel, Kubernetes kube, boolean forceUpdate) throws IOException {

        String configMapName = getLoggingConfigMapName();
        if (configMapName == null || configMapName.isEmpty())
            return;

        ConfigMap cm = kube.currentNamespace().getConfigMap(configMapName);
        if (cm == null || cm.getData() == null) {
            logger.error("Failed to load ConfigMap \"{}\"", configMapName);
            return;
        }

        String namespace = kube.getNamespaceName();
        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, configMapName);

        ConfigMap configMap = kube.getConfigMap(cm.getMetadata().getName());
        if (configMap != null && !forceUpdate) {
            // check if logLevel is changed
            Map<String, String> configMapData = configMap.getData();
            try {
                if (configMapData.get(LOGGING_JSON_KEY).equals(logLevel)) {
                    logger.info("Config map \"{}\" already exists, skipping", resourceLabel);
                    return;
                }
            } catch (NullPointerException e) {
                logger.error("Config map \"{}\" misses \"{}\" property", resourceLabel, LOGGING_JSON_KEY);
            }
        }

        // copy config map with updated log level value to namespace
        Map<String, String> cmData = cm.getData();
        try {
            logger.info("Creating \"{}\"", resourceLabel);

            for (String key : cmData.keySet()) {
                String data = cmData.get(key);

                if (data.contains(LOGGING_CXX_PATH_SUBSTRING))
                    data = data.replace(LOGGING_CXX_PATH_SUBSTRING, logLevel);

                if (data.contains(LOGGING_PYTHON_PATH_SUBSTRING))
                    data = data.replace(LOGGING_PYTHON_PATH_SUBSTRING, logLevel);

                if (data.contains(LOGGING_JAVA_PATH_SUBSTRING))
                    data = data.replace(LOGGING_JAVA_PATH_SUBSTRING, logLevel);

                cmData.put(key, data);
            }
            cmData.put(LOGGING_JSON_KEY, logLevel + "\n");

            cm.setMetadata(Kubernetes.createMetadataWithAnnotation(configMapName, resourceLabel));
            kube.createOrReplaceConfigMap(cm);
        } catch (Exception e) {
            logger.error("Exception copying \"{}\"", resourceLabel, e);
        }
    }

    private static String getLoggingConfigMapName() throws IOException {
        return Config.getInstance().getKubernetes().getConfigMaps().get(LOGGING_CONFIGMAP_PARAM);
    }
}
