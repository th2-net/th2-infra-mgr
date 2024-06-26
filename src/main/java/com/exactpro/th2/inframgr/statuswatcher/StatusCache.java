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

package com.exactpro.th2.inframgr.statuswatcher;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.SchemaEventRouter;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.k8s.KubernetesService;
import com.exactpro.th2.infrarepo.ResourceType;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class StatusCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusCache.class);

    private final NamespaceResources resources;

    private final Map<ResourcePath, Set<ResourcePath>> dependencies;

    private final Map<ResourcePath, ResourcePath> dependents;

    private final SchemaEventRouter eventRouter;

    private Kubernetes anonKube;

    @Autowired
    private Config config;

    @Autowired
    private KubernetesService kubernetesService;

    public StatusCache() {
        resources = new NamespaceResources();
        dependencies = new HashMap<>();
        dependents = new HashMap<>();
        eventRouter = SchemaEventRouter.getInstance();
    }

    private synchronized void update(ResourceCondition resource, String schema, Action action) {

        try {
            ResourcePath path = ResourcePath.fromMetadata(resource);
            ResourceType type = ResourceType.forKind(path.getKind());

            boolean isSchemaElement = type != null && type.isMangedResource();

            ResourcePath annotationPath = null;
            switch (action) {
                case ADD:
                    if (!isSchemaElement) {
                        annotationPath = ResourcePath.fromAnnotation(resource);
                        unindex(path);
                        index(annotationPath, path);
                    }
                    resources.add(path, resource);
                    break;
                case REMOVE:
                    if (!isSchemaElement) {
                        unindex(path);
                        annotationPath = ResourcePath.fromAnnotation(resource);
                    }
                    resources.remove(path);
            }

            if (isSchemaElement) {
                sendUpdateEvent(path, schema);
            } else {
                sendUpdateEvent(annotationPath, schema);
            }

        } catch (IllegalArgumentException ignored) {
            // ignoring resources whose annotations can not be properly decoded
            // as they are not considered complementary resources for schema elements
        }
    }

    public synchronized List<StatusUpdateEvent> getStatuses(String schema) {

        List<StatusUpdateEvent> events = new ArrayList<>();

        String namespace = anonKube.formatNamespaceName(schema);
        List<ResourceCondition> schemaElements = resources.getSchemaElements(namespace);
        if (schemaElements == null) {
            return null;
        }

        for (ResourceCondition resource : schemaElements) {
            events.add(new StatusUpdateEvent.Builder(schema)
                    .withKind(resource.getKind())
                    .withResourceName(resource.getName())
                    .withStatus(calculateStatus(resource).toString())
                    .build());
        }
        return events;
    }

    public synchronized List<ResourceCondition> getResourceDependencyStatuses(String schema,
                                                                              String kind,
                                                                              String resourceName) {

        String namespace = anonKube.formatNamespaceName(schema);
        return resources.getResourceElements(namespace, kind, resourceName);
    }

    private ResourceCondition.Status calculateStatus(ResourceCondition resource) {

        ResourceCondition.Status result = resource.getStatus();

        ResourcePath path = ResourcePath.fromMetadata(resource);
        Set<ResourcePath> components = dependencies.get(path);

        // if the resource does not have dependencies
        // return  resources status
        if (components == null) {
            return result;
        }

        // find all pods for this component
        // and compute the lowest status as a common status
        ResourceCondition.Status podsStatus = null;
        for (ResourcePath p : components) {
            if (p.getKind().equals("Pod")) {
                ResourceCondition.Status status = resources.get(p).getStatus();
                if (podsStatus == null || (podsStatus.value() > status.value())) {
                    podsStatus = status;
                }
            }
        }
        // assume pods status if pods were found
        if (podsStatus != null) {
            return podsStatus;
        }

        // find helm release for this component
        ResourceCondition.Status helmStatus = null;
        for (ResourcePath p : components) {
            if (p.getKind().equals("HelmRelease")) {
                helmStatus = resources.get(p).getStatus();
                break;
            }
        }
        if (helmStatus != null) {
            return helmStatus;
        }

        return resource.getStatus();
    }

    private void sendUpdateEvent(ResourcePath path, String schema) {
        ResourceCondition resource = resources.get(path);
        if (resource == null) {
            return;
        }

        eventRouter.addEvent(schema, new StatusUpdateEvent.Builder(schema)
                .withKind(path.getKind())
                .withResourceName(path.getResourceName())
                .withStatus(calculateStatus(resource).toString())
                .build());
    }

    private void index(ResourcePath annotationPath, ResourcePath resourcePath) {
        Set<ResourcePath> bucket = dependencies.computeIfAbsent(annotationPath, k -> new HashSet<>());
        bucket.add(resourcePath);
        dependents.put(resourcePath, annotationPath);
    }

    private void unindex(ResourcePath resourcePath) {
        ResourcePath path = dependents.get(resourcePath);
        dependents.remove(resourcePath);
        if (path != null) {
            Set<ResourcePath> bucket = dependencies.get(path);
            if (bucket != null) {
                bucket.remove(resourcePath);
            }
        }
    }

    @PostConstruct
    public void start() {
        LOGGER.info("Starting resource status monitoring");

        anonKube = kubernetesService.getKubernetes();

        anonKube.registerSharedInformersAll(new ResourceEventHandler<HasMetadata>() {

            @Override
            public void onAdd(HasMetadata obj) {
                ResourceCondition resource = ResourceCondition.extractFrom(obj);
                String schema = anonKube.extractSchemaName(resource.getNamespace());

                update(resource, schema, StatusCache.Action.ADD);
            }

            @Override
            public void onUpdate(HasMetadata oldObj, HasMetadata newObj) {
                ResourceCondition resource = ResourceCondition.extractFrom(newObj);
                String schema = anonKube.extractSchemaName(resource.getNamespace());

                update(resource, schema, StatusCache.Action.ADD);
            }

            @Override
            public void onDelete(HasMetadata obj, boolean deletedFinalStateUnknown) {
                ResourceCondition resource = ResourceCondition.extractFrom(obj);
                String schema = anonKube.extractSchemaName(resource.getNamespace());

                update(resource, schema, StatusCache.Action.REMOVE);
            }
        });

        anonKube.startInformers();
    }

    private enum Action {
        ADD,
        REMOVE
    }

}
