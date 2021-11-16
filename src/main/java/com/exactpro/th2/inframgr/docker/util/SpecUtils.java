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

package com.exactpro.th2.inframgr.docker.util;

import java.util.Map;

public class SpecUtils {

    private static final String IMAGE_NAME_ALIAS = "image-name";

    private static final String IMAGE_VERSION_ALIAS = "image-version";

    private static final String VERSION_RANGE_ALIAS = "version-range";

    private SpecUtils() {
    }

    public static String getImageName(Object sourceObj) {
        return getFieldAsString(sourceObj, IMAGE_NAME_ALIAS);
    }

    public static String getImageVersion(Object sourceObj) {
        return getFieldAsString(sourceObj, IMAGE_VERSION_ALIAS);
    }

    public static String getImageVersionRange(Object sourceObj) {
        return getFieldAsString(sourceObj, VERSION_RANGE_ALIAS);
    }

    public static void changeImageVersion(Object spec, String imageVersion) {
        Map<String, Object> specMap = (Map<String, Object>) spec;
        specMap.put(IMAGE_VERSION_ALIAS, imageVersion);
    }

    private static String getFieldAsString(Object spec, String path) {
        Map<String, Object> specMap = (Map<String, Object>) spec;
        var field = specMap.get(path);
        if (field != null) {
            return field.toString();
        }
        return null;
    }
}
