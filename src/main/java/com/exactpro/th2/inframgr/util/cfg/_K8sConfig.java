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

import java.util.Map;
import java.util.Set;

public class _K8sConfig {
    private boolean useCustomConfig;
    private String masterURL;
    private String defaultNamespace;
    private String apiVersion;
    private boolean ignoreInsecureHosts;
    private String clientCertificateFile;
    private String clientKeyFile;
    private String clientCertificate;
    private String clientKey;
    private String ingress;
    private Set<String> secretNames;
    private Map<String, String> configMaps;
    private String namespacePrefix;

    public boolean useCustomConfig() {
        return useCustomConfig;
    }

    public void setUseCustomConfig(boolean useCustomConfig) {
        this.useCustomConfig = useCustomConfig;
    }

    public String getMasterURL() {
        return masterURL;
    }

    public void setMasterURL(String masterURL) {
        this.masterURL = masterURL;
    }

    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    public void setDefaultNamespace(String namespace) {
        this.defaultNamespace = namespace;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public boolean ignoreInsecureHosts() {
        return ignoreInsecureHosts;
    }

    public void setIgnoreInsecureHosts(boolean ignoreInsecureHosts) {
        this.ignoreInsecureHosts = ignoreInsecureHosts;
    }

    public String getClientCertificateFile() {
        return clientCertificateFile;
    }

    public void setClientCertificateFile(String clientCertificateFile) {
        this.clientCertificateFile = clientCertificateFile;
    }

    public String getClientKeyFile() {
        return clientKeyFile;
    }

    public void setClientKeyFile(String clientKeyFile) {
        this.clientKeyFile = clientKeyFile;
    }

    public String getClientCertificate() {
        return clientCertificate;
    }

    public void setClientCertificate(String clientCertificate) {
        this.clientCertificate = clientCertificate;
    }

    public String getClientKey() {
        return clientKey;
    }

    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

    public Set<String> getSecretNames() {
        return secretNames;
    }

    public void setSecretNames(Set<String> secretNames) {
        this.secretNames = secretNames;
    }

    public String getNamespacePrefix() {
        return namespacePrefix == null ? "" : namespacePrefix;
    }

    public void setNamespacePrefix(String namespacePrefix) {
        this.namespacePrefix = namespacePrefix;
    }

    public Map<String, String> getConfigMaps() {
        return configMaps;
    }

    public void setConfigMaps(Map<String, String> configMaps) {
        this.configMaps = configMaps;
    }

    public String getIngress() {
        return ingress;
    }

    public void setIngress(String ingress) {
        this.ingress = ingress;
    }
}
