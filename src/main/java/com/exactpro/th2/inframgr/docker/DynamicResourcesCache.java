package com.exactpro.th2.inframgr.docker;

import java.util.*;

public enum DynamicResourcesCache {

    INSTANCE;

    private final Map<String, Map<String, DynamicResource>> cache = new HashMap<>();

    public Map<String, DynamicResource> getDynamicResources(String schema){
        if(cache.get(schema) != null) {
            return cache.get(schema);
        }
        return Collections.EMPTY_MAP;
    }

    public Set<String> getSchemas(){
        return cache.keySet();
    }

    public DynamicResource add(String schema, DynamicResource resource){
        cache.computeIfAbsent(schema, k -> new HashMap<>());
        return cache.get(schema).put(resource.getResourceName(), resource);
    }

    public DynamicResource remove(String schema, String name){
        var schemaCache = cache.get(schema);
        if(schemaCache == null || schemaCache.isEmpty()){
            return null;
        }else {
            return schemaCache.remove(name);
        }
    }
}
