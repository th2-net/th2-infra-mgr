/*
 * Copyright 2020-2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.inframgr;

import com.exactpro.th2.inframgr.errors.NotAcceptableException;
import com.exactpro.th2.inframgr.errors.ServiceException;
import com.exactpro.th2.inframgr.k8s.K8sCustomResource;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.statuswatcher.StatusCache;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import static com.exactpro.th2.inframgr.statuswatcher.ResourcePath.annotationFor;

@Controller
public class PodController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PodController.class);

    private static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";

    private static final String BAD_RESOURCE_NAME = "BAD_RESOURCE_NAME";

    @Autowired
    private Config config;

    @Autowired
    private StatusCache statusCache;

    @DeleteMapping("/pod/{schema}/{kind}/{resource}")
    public ResponseEntity<?> deleteResourcePods(
            @PathVariable(name = "schema") String schemaName,
            @PathVariable(name = "kind") String kind,
            @PathVariable(name = "resource") String resourceName,
            @RequestParam(name = "force", defaultValue = "false") boolean force) {

        try {
            // check schema name against valid pattern
            if (!K8sCustomResource.isSchemaNameValid(schemaName)) {
                throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");
            }

            try (Kubernetes kubernetes = new Kubernetes(config.getBehaviour(), config.getKubernetes(), schemaName)) {
                for (var resource : statusCache.getResourceDependencyStatuses(schemaName, kind, resourceName)) {
                    if (resource.getKind().equals(Kubernetes.KIND_POD)) {
                        String annotation = annotationFor(kubernetes.getNamespaceName(),
                                Kubernetes.KIND_POD, resource.getName());
                        try {
                            kubernetes.deletePodWithName(resource.getName(), force);
                            LOGGER.info("Deleted pod \"{}\", schema name \"{}\"", annotation, schemaName);
                        } catch (KubernetesClientException e) {
                            LOGGER.error("Could not delete pod \"{}\"", annotation, e);
                        }
                    }
                }
            }
            // TODO: return correct HTTP response
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, UNKNOWN_ERROR,
                    "Exception deleting pods for \"" + kind + "/" + resourceName +
                            "\" in schema \"" + schemaName + "\"", e);
        }
    }
}
