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

import com.exactpro.th2.infrarepo.RepositoryResource;

public class ResourcePath {

    private String namespace;

    private String kind;

    private String resourceName;

    public static String annotationFor(String namespace, String kind, String resourceName) {
        return String.format("%s:%s/%s", namespace, kind, resourceName);
    }

    public static String annotationFor(RepositoryResource resource, String namespace) {
        return annotationFor(
                namespace,
                resource.getKind(),
                resource.getMetadata().getName()
        );
    }

    public static ResourcePath fromAnnotation(ResourceCondition resource) {

        final String exceptionMessage = "Annotation decoding exception";

        String annotation = resource.getAntecedentAnnotation();
        if (annotation == null) {
            throw new IllegalArgumentException(exceptionMessage);
        }

        String[] s1 = annotation.split(":");
        if (s1.length != 2) {
            throw new IllegalArgumentException(exceptionMessage);
        }
        String[] s2 = s1[1].split("/");
        if (s2.length != 2) {
            throw new IllegalArgumentException(exceptionMessage);
        }

        ResourcePath path = new ResourcePath();
        path.namespace = s1[0].trim();
        path.kind = s2[0].trim();
        path.resourceName = s2[1].trim();
        if (path.namespace.length() == 0 || path.kind.length() == 0 || path.resourceName.length() == 0) {
            throw new IllegalArgumentException(exceptionMessage);
        }
        return path;
    }

    public static ResourcePath fromMetadata(ResourceCondition resource) {

        ResourcePath path = new ResourcePath();
        path.namespace = resource.getNamespace();
        path.kind = resource.getKind();
        path.resourceName = resource.getName();
        return path;
    }

    @Override
    public String toString() {
        return ResourcePath.annotationFor(namespace, kind, resourceName);
    }

    @Override
    public boolean equals(Object obj) {
        ResourcePath p = obj instanceof ResourcePath ? (ResourcePath) obj : null;
        if (p == null) {
            return false;
        }

        return namespace != null && namespace.equals(p.namespace)
                && kind != null && kind.equals(p.kind)
                && resourceName != null && resourceName.equals(p.resourceName);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    public String getNamespace() {
        return namespace;
    }

    public String getKind() {
        return kind;
    }

    public String getResourceName() {
        return resourceName;
    }

    private ResourcePath() {
    }
}
