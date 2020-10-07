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

package com.exactpro.th2.schema.inframgr.util.cfg;

public class _CassandraConfig {
    private String host;
    private String port;
    private String username;
    private String password;
    private String keyspacePrefix;
    private String hostForSchema;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getKeyspacePrefix() {
        return keyspacePrefix == null ? "" : keyspacePrefix;
    }

    public void setKeyspacePrefix(String keyspacePrefix) {
        this.keyspacePrefix = keyspacePrefix;
    }

    public String getHostForSchema() {
        return hostForSchema;
    }

    public void setHostForSchema(String hostForSchema) {
        this.hostForSchema = hostForSchema;
    }
}
