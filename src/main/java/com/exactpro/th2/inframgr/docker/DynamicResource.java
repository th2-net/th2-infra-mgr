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

package com.exactpro.th2.inframgr.docker;

public class DynamicResource {
    private final String resourceName;
    private final String image;
    private final String currentVersion;
    private final String versionRange;
    private final String schema;


    public DynamicResource(String resourceName, String image, String currentVersion, String versionRange, String schema) {
        this.resourceName = resourceName;
        this.image = image;
        this.currentVersion = currentVersion;
        this.versionRange = versionRange;
        this.schema = schema;
    }

    public String getName() {
        return resourceName;
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


}
