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

package com.exactpro.th2.inframgr.statuswatcher;

import com.exactpro.th2.infrarepo.ResourceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class NamespaceResources {
    Map<String, TypedResources> cache = new HashMap<>();

    ResourceCondition get(ResourcePath path) {
        TypedResources resources = cache.get(path.getNamespace());
        return resources == null ? null : resources.get(path);
    }

    void add(ResourcePath path, ResourceCondition resource) {
        TypedResources resources = cache.computeIfAbsent(path.getNamespace(), k -> new TypedResources());
        resources.add(path, resource);
    }

    void remove(ResourcePath path) {
        TypedResources resources = cache.get(path.getNamespace());
        if (resources != null) {
            resources.remove(path);
        }
    }

    public static class TypedResources {
        Map<String, Resources> cache = new HashMap<>();

        ResourceCondition get(ResourcePath path) {
            Resources resources = cache.get(path.getKind());
            return resources == null ? null : resources.get(path);
        }

        void add(ResourcePath path, ResourceCondition resource) {
            Resources resources = cache.computeIfAbsent(path.getKind(), k -> new Resources());
            resources.add(path, resource);
        }

        void remove(ResourcePath path) {
            Resources resources = cache.get(path.getKind());
            if (resources != null) {
                resources.remove(path);
            }
        }
    }

    public static class Resources {
        Map<String, ResourceCondition> cache = new HashMap<>();

        ResourceCondition get(ResourcePath path) {
            return cache.get(path.getResourceName());
        }

        boolean add(ResourcePath path, ResourceCondition resource) {
            cache.put(path.getResourceName(), resource);
            return true;
        }

        void remove(ResourcePath path) {
            cache.remove(path.getResourceName());
        }
    }

    public List<ResourceCondition> getSchemaElements(String namespace) {

        TypedResources typedResources = cache.get(namespace);
        if (typedResources == null) {
            return null;
        }

        List<ResourceCondition> elements = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            if (type.isK8sResource() && !type.equals(ResourceType.HelmRelease)) {
                Resources resources = typedResources.cache.get(type.kind());
                if (resources == null) {
                    continue;
                }
                for (ResourceCondition resource : resources.cache.values()) {
                    elements.add(resource);
                }
            }
        }
        return elements;
    }

    public List<ResourceCondition> getResourceElements(String namespace, String kind, String resourceName) {

        TypedResources typedResources = cache.get(namespace);
        if (typedResources == null) {
            return null;
        }

        String annotation = ResourcePath.annotationFor(namespace, kind, resourceName);
        List<ResourceCondition> elements = new ArrayList<>();

        for (Resources resources : typedResources.cache.values()) {
            for (ResourceCondition resource : resources.cache.values()) {
                if ((resource.getKind().equals(kind) && resource.getName().equals(resourceName))
                        || annotation.equals(resource.getAntecedentAnnotation())) {
                    elements.add(resource);
                }
            }
        }

        return elements;
    }

}
