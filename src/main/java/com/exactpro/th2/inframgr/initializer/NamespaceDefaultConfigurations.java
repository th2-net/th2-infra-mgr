package com.exactpro.th2.inframgr.initializer;

import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import com.exactpro.th2.infrarepo.NamespaceDefaults;
import io.fabric8.kubernetes.api.model.ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.exactpro.th2.inframgr.initializer.SchemaInitializer.NAMESPACE_DEFAULTS_CONFIGMAP;
import static com.exactpro.th2.inframgr.util.SourceHashUtil.setSourceHash;

public class NamespaceDefaultConfigurations {
    private static final Logger logger = LoggerFactory.getLogger(Th2BoxConfigurations.class);

    private static final String BOOK_NAME_KEY = "bookName";

    public static void synchronizeNamespaceDefaultsMap(NamespaceDefaults namespaceDefaults, Kubernetes kube) {
        String namespace = kube.getNamespaceName();
        String resourceLabel = ResourcePath.annotationFor(namespace, Kubernetes.KIND_CONFIGMAP, NAMESPACE_DEFAULTS_CONFIGMAP);

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
                configMap.setMetadata(Kubernetes.createMetadataWithAnnotation(NAMESPACE_DEFAULTS_CONFIGMAP, resourceLabel));
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
