/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.schema.schemamanager;

import rx.Observable;
import rx.subjects.PublishSubject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SchemaEventRouter {
    private static class EventCache extends LinkedHashMap<String, SchemaEvent> {
        private static final int CACHE_CAPACITY = 1024;
        @Override
        public boolean removeEldestEntry(Map.Entry<String, SchemaEvent> eldest) {
            return this.size() >= CACHE_CAPACITY;
        }
    }

    private static volatile SchemaEventRouter instance;
    private PublishSubject<SchemaEvent> subject;
    private Map<String, EventCache> acceptedEvents;

    private SchemaEventRouter() {
        subject = PublishSubject.create();
        acceptedEvents = new HashMap<>();
    }

    public static SchemaEventRouter getInstance() {
        if (instance == null) {
            synchronized(SchemaEventRouter.class) {
                if (instance == null)
                    instance = new SchemaEventRouter();
            }
        }
        return instance;
    }


    private EventCache getEventCache(String eventType) {

        EventCache eventCache = acceptedEvents.get(eventType);
        if (eventCache == null)
            synchronized(acceptedEvents) {
                eventCache = acceptedEvents.get(eventType);
                if (eventCache == null)
                    eventCache = new EventCache();
                acceptedEvents.put(eventType, eventCache);
            }
        return eventCache;
    }

    public boolean isEventCached(SchemaEvent event) {
        EventCache cache = getEventCache(event.getEventType());
        return cache.containsKey(event.getEventKey());
    }

    public boolean addEventIfNotCached(SchemaEvent event) {
        EventCache eventCache = getEventCache(event.getEventType());
        boolean doSend = false;
        synchronized (eventCache) {
            if (!eventCache.containsKey(event.getEventKey())) {
                eventCache.put(event.getEventKey(), event);
                doSend = true;
            }
        }
        if (doSend)
            addEvent(event);
        return doSend;
    }

    public void addEvent(SchemaEvent event) {

        EventCache eventCache = getEventCache(event.getEventType());
        synchronized (eventCache) {
            eventCache.put(event.getEventKey(), event);
        }
        subject.onNext(event);
    }

    public Observable<SchemaEvent> getObservable() {
        return subject.asObservable();
    }
}
