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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public enum DynamicResourcesCache {

    INSTANCE;

    private final Map<String, Map<String, DynamicResource>> cache = new ConcurrentHashMap<>();

    public synchronized Collection<DynamicResource> getDynamicResourcesCopy(String schema) {
        if (cache.get(schema) != null) {
            return Collections.unmodifiableCollection(cache.get(schema).values());
        }
        return Collections.EMPTY_LIST;
    }

    public synchronized Collection<String> getSchemas() {
        return Collections.unmodifiableCollection(cache.keySet());
    }

    public synchronized DynamicResource add(String schema, DynamicResource resource) {
        cache.computeIfAbsent(schema, k -> new ConcurrentHashMap<>());
        return cache.get(schema).put(resource.getName(), resource);
    }

    public synchronized DynamicResource removeResource(String schema, String name) {
        var schemaCache = cache.get(schema);
        if (schemaCache == null || schemaCache.isEmpty()) {
            return null;
        } else {
            return schemaCache.remove(name);
        }
    }

    public synchronized void removeSchema(String schema) {
        cache.remove(schema);
    }
}
