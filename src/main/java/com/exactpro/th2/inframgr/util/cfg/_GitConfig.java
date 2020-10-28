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

package com.exactpro.th2.inframgr.util.cfg;

public class _GitConfig {
    private String remoteRepository;
    private boolean ignoreInsecureHosts;

    private String localRepositoryRoot;

    private String privateKeyFile;

    private String privateKey;
    private byte[] privateKeyBytes;

    public String getRemoteRepository() {
        return remoteRepository;
    }

    public void setRemoteRepository(String remoteRepository) {
        this.remoteRepository = remoteRepository;
    }

    public boolean ignoreInsecureHosts() {
        return ignoreInsecureHosts;
    }

    public void setIgnoreInsecureHosts(boolean ignoreInsecureHosts) {
        this.ignoreInsecureHosts = ignoreInsecureHosts;
    }

    public String getLocalRepositoryRoot() {
        return localRepositoryRoot;
    }

    public void setLocalRepositoryRoot(String localRepositoryRoot) {
        this.localRepositoryRoot = localRepositoryRoot;
    }

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    public void setPrivateKeyFile(String privateKeyFile) {
        this.privateKeyFile = privateKeyFile;
    }

    public byte[] getPrivateKey() {
        return privateKeyBytes;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
        this.privateKeyBytes = privateKey.getBytes();
    }
}
