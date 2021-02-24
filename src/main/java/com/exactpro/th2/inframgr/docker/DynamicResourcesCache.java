package com.exactpro.th2.inframgr.docker;

import java.util.*;

public enum DynamicResourcesCache {

    INSTANCE;

    private final Map<String, Map<String, DynamicResource>> cache = new HashMap<>();

    public Collection<DynamicResource> getDynamicResources(String schema){
        if(cache.get(schema) != null) {
            return cache.get(schema).values();
        }
        return Collections.EMPTY_LIST;
    }

    public Collection<DynamicResource> getAllDynamicResources(){
        Collection<DynamicResource> allResources = Collections.EMPTY_LIST;
        for(String schema: cache.keySet()){
            allResources.addAll(getDynamicResources(schema));
        }
        return allResources;
    }

    public DynamicResource add(String schema, String name, DynamicResource resource){
        cache.computeIfAbsent(schema, k -> new HashMap<>());
        return cache.get(schema).put(name, resource);
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
