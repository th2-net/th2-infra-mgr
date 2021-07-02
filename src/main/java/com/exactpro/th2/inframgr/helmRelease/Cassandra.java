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

package com.exactpro.th2.inframgr.helmRelease;

import java.util.HashMap;
import java.util.Map;

public class Cassandra {

    private String keyspacePrefix;
    private String username;
    private String password;
    private String host;
    private int port;

    private String datacenter;
    private int timeout;
    private String instanceName;
    private Map<String, String> schemaNetworkTopology;

    public String getKeyspacePrefix() {
        return keyspacePrefix == null ? "" : keyspacePrefix;
    }

    public void setKeyspacePrefix(String keyspacePrefix) {
        this.keyspacePrefix = keyspacePrefix;
    }

    public String getUsername() {
        return username == null ? "" : username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password == null ? "" : password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getHost() {
        return host == null ? "" : host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatacenter() {
        return datacenter == null ? "" : datacenter;
    }

    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getInstanceName() {
        return instanceName == null ? "" : instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public Map<String, String> getSchemaNetworkTopology() {
        return schemaNetworkTopology == null ? new HashMap<>() : schemaNetworkTopology;
    }

    public void setSchemaNetworkTopology(Map<String, String> schemaNetworkTopology) {
        this.schemaNetworkTopology = schemaNetworkTopology;
    }
}
