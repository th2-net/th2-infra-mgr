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

public class _CassandraConfig {

    private String keyspacePrefix;

    private String secret;

    public String getKeyspacePrefix() {
        return keyspacePrefix == null ? "" : keyspacePrefix;
    }

    public void setKeyspacePrefix(String keyspacePrefix) {
        this.keyspacePrefix = keyspacePrefix;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSecret() {
        return secret == null ? "" : secret;
    }
}
