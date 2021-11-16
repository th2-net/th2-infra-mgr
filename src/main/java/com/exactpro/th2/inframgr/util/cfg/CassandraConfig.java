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

package com.exactpro.th2.inframgr.util.cfg;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CassandraConfig {

    private String keyspacePrefix;

    private String secret;

    private String username;

    private String password;

    private String host;

    private int port;

    private String dataCenter;

    private int timeout;

    private String instanceName;

    private int pageSize;

    private int cradleMaxEventBatchSize;

    private int cradleMaxMessageBatchSize;

    private Map<String, Integer> networkTopologyStrategy;

    public String getKeyspacePrefix() {
        return keyspacePrefix == null ? "" : keyspacePrefix;
    }

    public String getSecret() {
        return secret == null ? "" : secret;
    }

    public String getUsername() {
        return username == null ? "" : username;
    }

    public String getPassword() {
        return password == null ? "" : password;
    }

    public String getHost() {
        return host == null ? "" : host;
    }

    public int getPort() {
        return port;
    }

    public String getDataCenter() {
        return dataCenter == null ? "" : dataCenter;
    }

    public int getTimeout() {
        return timeout;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getCradleMaxEventBatchSize() {
        return cradleMaxEventBatchSize;
    }

    public int getCradleMaxMessageBatchSize() {
        return cradleMaxMessageBatchSize;
    }

    public String getInstanceName() {
        return instanceName == null ? "" : instanceName;
    }

    public Map<String, Integer> getNetworkTopologyStrategy() {
        return networkTopologyStrategy == null ? new HashMap<>() : networkTopologyStrategy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CassandraConfig)) {
            return false;
        }
        CassandraConfig that = (CassandraConfig) o;
        return getPort() == that.getPort() && getTimeout() == that.getTimeout()
                && Objects.equals(getKeyspacePrefix(), that.getKeyspacePrefix())
                && Objects.equals(getSecret(), that.getSecret())
                && Objects.equals(getUsername(), that.getUsername())
                && Objects.equals(getPassword(), that.getPassword())
                && Objects.equals(getHost(), that.getHost())
                && Objects.equals(getDataCenter(), that.getDataCenter())
                && Objects.equals(getInstanceName(), that.getInstanceName())
                && Objects.equals(getPageSize(), that.getPageSize())
                && Objects.equals(getCradleMaxEventBatchSize(), that.getCradleMaxEventBatchSize())
                && Objects.equals(getCradleMaxMessageBatchSize(), that.getCradleMaxMessageBatchSize())
                && Objects.equals(getNetworkTopologyStrategy(), that.getNetworkTopologyStrategy());
    }
}
