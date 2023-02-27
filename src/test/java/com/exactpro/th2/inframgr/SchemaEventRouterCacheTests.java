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

package com.exactpro.th2.inframgr;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchemaEventRouterCacheTests {

    static SchemaEventRouter router;

    private final String schema1 = "test-schema";

    private final String schema2 = "test-schema-2";

    @BeforeAll
    static void createRouter() {
        router = SchemaEventRouter.getInstance();
    }

    @AfterEach
    void clearCache() {
        router.removeEventsForSchema(schema1);
        router.removeEventsForSchema(schema2);
    }

    @Test
    @Order(1)
    void testEmpty() {
        EventType1 t1e1 = new EventType1("abc");
        assertFalse(router.isEventCached(schema1, t1e1));
        assertFalse(router.isEventCached(schema2, t1e1));
    }

    @Test
    void testExists() {
        EventType1 t1e1 = new EventType1("abc");
        router.addEvent(schema1, t1e1);
        assertTrue(router.isEventCached(schema1, t1e1));
    }

    @Test
    void testExistsInCorrectSchema() {
        EventType1 t1e1 = new EventType1("abc");
        router.addEvent(schema1, t1e1);
        assertTrue(router.isEventCached(schema1, t1e1));
        assertFalse(router.isEventCached(schema2, t1e1));
    }

    @Test
    void testRemoveSchemaEvents() {
        EventType1 t1e1 = new EventType1("abc");
        router.addEvent(schema1, t1e1);
        assertTrue(router.isEventCached(schema1, t1e1));
        router.removeEventsForSchema(schema1);
        assertFalse(router.isEventCached(schema1, t1e1));
    }

    @Test
    void testCrossTypeConflicts() {
        EventType1 t1e1 = new EventType1("abc");
        EventType2 t2e1 = new EventType2("abc");

        router.addEvent(schema1, t1e1);
        assertTrue(router.isEventCached(schema1, t1e1));
        assertFalse(router.isEventCached(schema1, t2e1));

        router.addEvent(schema1, t2e1);
        assertTrue(router.isEventCached(schema1, t1e1));
        assertTrue(router.isEventCached(schema1, t2e1));
    }

    @Test
    void testCacheExpiration() {

        // add type1 event
        EventType1 t1e1 = new EventType1("abc");
        router.addEvent(schema1, t1e1);
        assertTrue(router.isEventCached(schema1, t1e1));

        // populate with type2 events
        for (int i = 0; i < 2000; i++) {
            EventType2 t2 = new EventType2(String.format("a%08x", i));
            router.addEvent(schema1, t2);
        }

        //check that type1 event did not expire
        assertTrue(router.isEventCached(schema1, t1e1));

        // trigger type2 event expirations
        for (int i = 0; i < 2000; i++) {
            EventType2 t2 = new EventType2(String.format("b%08x", i));
            router.addEvent(schema1, t2);
        }

        //check that type1 event did not expire
        assertTrue(router.isEventCached(schema1, t1e1));

        // first stage type1 events should be all expired
        for (int i = 0; i < 2000; i++) {
            EventType2 t2 = new EventType2(String.format("a%08x", i));
            assertFalse(router.isEventCached(schema1, t2));
        }

        // there should be some type2 events
        for (int i = 1900; i < 2000; i++) {
            EventType2 t2 = new EventType2(String.format("b%08x", i));
            assertTrue(router.isEventCached(schema1, t2));
        }
    }

    private abstract static class BaseEvent extends SchemaEvent {

        private final String key;

        private BaseEvent(String key) {
            super(key);
            this.key = key;
        }

        @Override
        public String getEventBody() {
            return "event-for:" + key;
        }

        @Override
        public String getEventKey() {
            return key;
        }
    }

    private class EventType1 extends BaseEvent {
        private EventType1(String key) {
            super(key);
        }

        @Override
        public String getEventType() {
            return "type1";
        }
    }

    private class EventType2 extends BaseEvent {
        private EventType2(String key) {
            super(key);
        }

        @Override
        public String getEventType() {
            return "type2";
        }
    }
}
