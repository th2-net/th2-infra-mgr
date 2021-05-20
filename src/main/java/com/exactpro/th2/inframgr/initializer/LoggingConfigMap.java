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

import static com.exactpro.th2.inframgr.util.SourceHashUtil.setSourceHash;

public class LoggingConfigMap {

    private static final Logger logger = LoggerFactory.getLogger(LoggingConfigMap.class);

    private static final String LOGGING_CONFIGMAP_PARAM = "logging";

    private static final String LOGGING_CONFIGMAP_NAME_IN_NAMESPACE = "logging-config";

    private static final String TH2_LOGGING_JSON_KEY = "logLevelTh2";
    private static final String ROOT_LOGGING_JSON_KEY = "logLevelRoot";

    private static final String LOGGING_CXX_PATH_SUBSTRING = "${LOGLEVEL_CXX}";
    private static final String LOGGING_JAVA_PATH_SUBSTRING = "${LOGLEVEL_JAVA}";
    private static final String LOGGING_PYTHON_PATH_SUBSTRING = "${LOGLEVEL_PYTHON}";
    private static final String LOGGING_ROOT_PATH_SUBSTRING = "${LOGLEVEL_ROOT}";

    public static void checkLoggingConfigMap(RepositoryResource resource, String logLevelRoot, String logLevelTh2, Kubernetes kube) throws IOException {
        if (resource.getMetadata() != null && resource.getMetadata().getName().equals(getLoggingConfigMapName())) {
            copyLoggingConfigMap(logLevelRoot, logLevelTh2, kube, true);
        }
    }

    public static void copyLoggingConfigMap(String logLevelRoot, String logLevelTh2, Kubernetes kube) throws IOException {
        copyLoggingConfigMap(logLevelRoot, logLevelTh2, kube, false);
    }

    public static void copyLoggingConfigMap(String logLevelRoot, String logLevelTh2, Kubernetes kube, boolean forceUpdate) throws IOException {

        String configMapName = getLoggingConfigMapName();
        if (configMapName == null || configMapName.isEmpty())
            return;

        ConfigMap cm = kube.currentNamespace().getConfigMap(configMapName);
        if (cm == null || cm.getData() == null) {
            logger.error("Failed to load ConfigMap \"{}\"", configMapName);
            return;
        }

        String namespace = kube.getNamespaceName();
        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, LOGGING_CONFIGMAP_NAME_IN_NAMESPACE);

        ConfigMap configMap = kube.getConfigMap(LOGGING_CONFIGMAP_NAME_IN_NAMESPACE);
        if (configMap != null && !forceUpdate) {
            // check if logLevel is changed
            Map<String, String> configMapData = configMap.getData();
            try {
                if (configMapData.get(TH2_LOGGING_JSON_KEY).equals(logLevelTh2 + "\n")
                        && configMapData.get(ROOT_LOGGING_JSON_KEY).equals(logLevelRoot + "\n")) {
                    logger.info("Config map \"{}\" already exists, skipping", resourceLabel);
                    return;
                }
            } catch (NullPointerException e) {
                logger.error("Config map \"{}\" misses \"{}\" or \"{}\" property", resourceLabel,
                        TH2_LOGGING_JSON_KEY, ROOT_LOGGING_JSON_KEY);
            }
        }

        // copy config map with updated log level value to namespace
        Map<String, String> cmData = cm.getData();
        try {
            logger.info("Creating \"{}\"", resourceLabel);

            for (String key : cmData.keySet()) {
                String data = cmData.get(key);

                if (data.contains(LOGGING_ROOT_PATH_SUBSTRING))
                    data = data.replace(LOGGING_ROOT_PATH_SUBSTRING, logLevelRoot);

                if (data.contains(LOGGING_CXX_PATH_SUBSTRING))
                    data = data.replace(LOGGING_CXX_PATH_SUBSTRING, logLevelTh2);

                if (data.contains(LOGGING_PYTHON_PATH_SUBSTRING))
                    data = data.replace(LOGGING_PYTHON_PATH_SUBSTRING, logLevelTh2);

                if (data.contains(LOGGING_JAVA_PATH_SUBSTRING))
                    data = data.replace(LOGGING_JAVA_PATH_SUBSTRING, logLevelTh2);

                cmData.put(key, data);
            }
            cmData.put(TH2_LOGGING_JSON_KEY, logLevelTh2 + "\n");
            cmData.put(ROOT_LOGGING_JSON_KEY, logLevelRoot + "\n");

            cm.setMetadata(Kubernetes.createMetadataWithAnnotation(LOGGING_CONFIGMAP_NAME_IN_NAMESPACE, resourceLabel));
            setSourceHash(cm.getMetadata().getAnnotations(), cmData);
            kube.createOrReplaceConfigMap(cm);
        } catch (Exception e) {
            logger.error("Exception copying \"{}\"", resourceLabel, e);
        }
    }

    private static String getLoggingConfigMapName() throws IOException {
        return Config.getInstance().getKubernetes().getConfigMaps().get(LOGGING_CONFIGMAP_PARAM);
    }
}
