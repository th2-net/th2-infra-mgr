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

package com.exactpro.th2.inframgr.docker.monitoring;

import java.util.Objects;

public class DynamicResource {

    private final String resourceName;

    private final String kind;

    private final String image;

    private final String currentVersion;

    private final String versionRange;

    private final String schema;

    public DynamicResource(String resourceName,
                           String kind,
                           String image,
                           String currentVersion,
                           String versionRange,
                           String schema) {
        this.resourceName = resourceName;
        this.kind = kind;
        this.image = image;
        this.currentVersion = currentVersion;
        this.versionRange = versionRange;
        this.schema = schema;
    }

    public String getName() {
        return resourceName;
    }

    public String getKind() {
        return kind;
    }

    public String getImage() {
        return image;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getVersionRange() {
        return versionRange;
    }

    public String getSchema() {
        return schema;
    }

    public String getAnnotation() {
        return String.format("%s.%s", schema, getName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DynamicResource)) {
            return false;
        }
        DynamicResource that = (DynamicResource) o;
        return Objects.equals(resourceName, that.resourceName) &&
                Objects.equals(kind, that.kind) &&
                Objects.equals(image, that.image) &&
                Objects.equals(currentVersion, that.currentVersion) &&
                Objects.equals(versionRange, that.versionRange) &&
                Objects.equals(schema, that.schema);
    }

    @Override
    public String toString() {
        return "DynamicResource{" +
                "resourceName='" + resourceName + '\'' +
                ", kind='" + kind + '\'' +
                ", image='" + image + '\'' +
                ", currentVersion='" + currentVersion + '\'' +
                ", versionRange='" + versionRange + '\'' +
                ", schema='" + schema + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceName, kind, image,
                currentVersion, versionRange, schema);
    }
}
