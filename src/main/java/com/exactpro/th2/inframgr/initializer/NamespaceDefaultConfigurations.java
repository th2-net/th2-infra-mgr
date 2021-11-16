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
import com.exactpro.th2.infrarepo.NamespaceDefaults;
import io.fabric8.kubernetes.api.model.ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.exactpro.th2.inframgr.initializer.SchemaInitializer.NAMESPACE_DEFAULTS_CONFIGMAP;
import static com.exactpro.th2.inframgr.k8s.Kubernetes.createMetadataWithAnnotation;
import static com.exactpro.th2.inframgr.statuswatcher.ResourcePath.annotationFor;
import static com.exactpro.th2.inframgr.util.SourceHashUtil.setSourceHash;

public class NamespaceDefaultConfigurations {
    private static final Logger logger = LoggerFactory.getLogger(Th2BoxConfigurations.class);

    private static final String BOOK_NAME_KEY = "bookName";

    public static void synchronizeNamespaceDefaultsMap(NamespaceDefaults namespaceDefaults, Kubernetes kube) {
        String namespace = kube.getNamespaceName();
        String resourceLabel = annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, NAMESPACE_DEFAULTS_CONFIGMAP);

        if (namespaceDefaults == null || namespaceDefaults.getBookName() == null) {
            logger.debug("No custom configuration present for namespace values. Using default config map");
            return;
        }

        ConfigMap configMap = kube.getConfigMap(NAMESPACE_DEFAULTS_CONFIGMAP);

        if (configMap == null) {
            logger.error("Failed to load ConfigMap \"{}\"", NAMESPACE_DEFAULTS_CONFIGMAP);
            return;
        }

        Map<String, String> data = configMap.getData();
        String newBookName = namespaceDefaults.getBookName();
        if (data == null) {
            data = new HashMap<>();
        }
        if (data.get(BOOK_NAME_KEY) == null || !data.get(BOOK_NAME_KEY).equals(newBookName)) {
            try {
                logger.info("Updating \"{}\"", resourceLabel);
                data.put(BOOK_NAME_KEY, newBookName);
                configMap.setMetadata(createMetadataWithAnnotation(NAMESPACE_DEFAULTS_CONFIGMAP, resourceLabel));
                setSourceHash(configMap.getMetadata().getAnnotations(), data);
                kube.createOrReplaceConfigMap(configMap);
            } catch (Exception e) {
                logger.error("Exception Updating \"{}\"", resourceLabel, e);
                throw e;
            }

        } else {
            logger.info("Config map \"{}\" is up to date", resourceLabel);
        }
    }
}
