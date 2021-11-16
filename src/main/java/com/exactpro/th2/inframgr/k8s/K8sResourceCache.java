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

package com.exactpro.th2.inframgr.k8s;

import com.exactpro.th2.infrarepo.RepositoryResource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public enum K8sResourceCache {
    INSTANCE;

    public static class CacheEntry {

        private boolean markedDeleted;

        private String hash;

        public boolean isMarkedAsDeleted() {
            return markedDeleted;
        }

        public void markAsDeleted() {
            this.markedDeleted = true;
        }

        public String getHash() {
            return hash;
        }

        private void setHash(String hash) {
            this.hash = hash;
        }
    }

    private final Map<String, CacheEntry> cache = new HashMap<>();

    private final Map<String, Lock> locks = new HashMap<>();

    private String keyFor(String namespace, String type, String resourceName) {
        return String.format("%s:%s/%s", namespace, type, resourceName);
    }

    public synchronized void add(String namespace, K8sCustomResource resource) {

        String key = keyFor(namespace, resource.getKind(), resource.getMetadata().getName());

        CacheEntry entry = new CacheEntry();
        entry.setHash(resource.getSourceHash());

        cache.put(key, entry);
    }

    public synchronized void add(String namespace, RepositoryResource resource) {

        String key = keyFor(namespace, resource.getKind(), resource.getMetadata().getName());
        CacheEntry entry = new CacheEntry();
        entry.setHash(resource.getSourceHash());

        cache.put(key, entry);
    }

    public synchronized CacheEntry get(String namespace, String resourceType, String resourceName) {

        String key = keyFor(namespace, resourceType, resourceName);
        return cache.get(key);
    }

    public synchronized CacheEntry get(String namespace, K8sCustomResource resource) {

        return get(namespace, resource.getKind(), resource.getMetadata().getName());
    }

    public synchronized void remove(String namespace, String resourceType, String resourceName) {

        CacheEntry entry = get(namespace, resourceType, resourceName);
        if (entry != null) {
            entry.markAsDeleted();
        }
    }

    public synchronized Lock lockFor(String namespace, String resourceType, String resourceName) {

        String key = keyFor(namespace, resourceType, resourceName);
        return locks.computeIfAbsent(key, v -> new ReentrantLock());
    }

}
