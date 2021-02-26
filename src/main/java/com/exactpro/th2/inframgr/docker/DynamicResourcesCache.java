package com.exactpro.th2.inframgr.docker;

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

    public synchronized DynamicResource remove(String schema, String name) {
        var schemaCache = cache.get(schema);
        if (schemaCache == null || schemaCache.isEmpty()) {
            return null;
        } else {
            return schemaCache.remove(name);
        }
    }
}
