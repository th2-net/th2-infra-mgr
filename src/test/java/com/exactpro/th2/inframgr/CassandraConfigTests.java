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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CassandraConfigTests {

    private Config expected;
    private Config actual;

    private void setPath(String path) {
        System.setProperty(Config.CONFIG_DIR_SYSTEM_PROPERTY, path);
        expected = new Config();
        actual = new Config();
    }

    @Test
    void testFullCassandra() throws IOException {
        setPath("src/test/resources/cassandraConfig/fullCassandra");

        Config.CassandraConfig cassandra = new Config.CassandraConfig();
        cassandra.setKeyspacePrefix("prefix");
        cassandra.setUsername("user");
        cassandra.setPassword("passwd");
        cassandra.setHost("host");
        cassandra.setPort(8081);
        cassandra.setDatacenter("datacenter");
        cassandra.setTimeout(5);
        cassandra.setInstanceName("name");
        Map<String, String> map = new HashMap<>();
        map.put("datacenter1", "Dcenter");
        cassandra.setSchemaNetworkTopology(map);

        expected.setCassandra(cassandra);
        actual.readConfiguration();

        Assertions.assertEquals(expected.getCassandra(), actual.getCassandra());
    }

    @Test
    void testDefaultCassandra() throws IOException {
        setPath("src/test/resources/cassandraConfig/defaultCassandra");

        Config.CassandraConfig cassandra = new Config.CassandraConfig();

        expected.setCassandra(cassandra);
        actual.readConfiguration();

        Assertions.assertEquals(expected.getCassandra(), actual.getCassandra());

        Assertions.assertEquals("", actual.getCassandra().getKeyspacePrefix());
        Assertions.assertEquals("", actual.getCassandra().getUsername());
        Assertions.assertEquals("", actual.getCassandra().getPassword());
        Assertions.assertEquals("", actual.getCassandra().getHost());
        Assertions.assertEquals("", actual.getCassandra().getDatacenter());
        Assertions.assertEquals("", actual.getCassandra().getInstanceName());
        Assertions.assertEquals(0, actual.getCassandra().getTimeout());
        Assertions.assertEquals(0, actual.getCassandra().getPort());
        Assertions.assertEquals(new HashMap<>(), actual.getCassandra().getSchemaNetworkTopology());
    }
}
