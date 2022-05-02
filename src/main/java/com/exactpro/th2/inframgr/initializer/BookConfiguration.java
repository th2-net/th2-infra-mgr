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
import com.exactpro.th2.infrarepo.BookConfig;
import io.fabric8.kubernetes.api.model.ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.exactpro.th2.inframgr.initializer.SchemaInitializer.BOOK_CONFIG_CM_NAME;
import static com.exactpro.th2.inframgr.util.AnnotationUtils.stamp;

public class BookConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(BookConfiguration.class);

    private static final String DEFAULT_BOOK = "defaultBook";

    public static void synchronizeBookConfig(BookConfig bookConfig, Kubernetes kube, String fullCommitRef) {
        String namespace = kube.getNamespaceName();
        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, BOOK_CONFIG_CM_NAME);

        ConfigMap defaultConfigMap = kube.currentNamespace().getConfigMap(BOOK_CONFIG_CM_NAME);

        if (defaultConfigMap == null || defaultConfigMap.getData() == null) {
            logger.error("Failed to load ConfigMap \"{}\" from default namespace", BOOK_CONFIG_CM_NAME);
            return;
        }

        try {
            Map<String, String> defaultData = defaultConfigMap.getData();
            ConfigMap configMapInSchemaNamespace = kube.getConfigMap(BOOK_CONFIG_CM_NAME);
            Map<String, String> dataInSchemaNamespace = configMapInSchemaNamespace.getData();

            if (bookConfig == null || bookConfig.getDefaultBook() == null) {
                logger.debug("No custom configuration present for namespace values. Using default config map");
                if (defaultData.get(DEFAULT_BOOK).equals(dataInSchemaNamespace.get(DEFAULT_BOOK))) {
                    logger.info("Config map \"{}\" is up to date", resourceLabel);
                    return;
                }
                try {
                    logger.info("Resetting to default \"{}\"", resourceLabel);
                    dataInSchemaNamespace.put(DEFAULT_BOOK, defaultData.get(DEFAULT_BOOK));
                    stamp(configMapInSchemaNamespace, fullCommitRef);
                    kube.createOrReplaceConfigMap(configMapInSchemaNamespace);
                } catch (Exception e) {
                    logger.error("Exception Resetting \"{}\"", resourceLabel, e);
                    throw e;
                }
                return;
            }

            String newDefaultBookName = bookConfig.getDefaultBook();

            if (!dataInSchemaNamespace.get(DEFAULT_BOOK).equals(newDefaultBookName)) {
                try {
                    logger.info("Updating \"{}\"", resourceLabel);
                    dataInSchemaNamespace.put(DEFAULT_BOOK, newDefaultBookName);
                    stamp(configMapInSchemaNamespace, fullCommitRef);
                    kube.createOrReplaceConfigMap(configMapInSchemaNamespace);
                } catch (Exception e) {
                    logger.error("Exception Updating \"{}\"", resourceLabel, e);
                    throw e;
                }

            } else {
                logger.info("Config map \"{}\" is up to date", resourceLabel);
            }
        } catch (NullPointerException npe) {
            logger.error(npe.getMessage(), npe);
        }
    }
}
