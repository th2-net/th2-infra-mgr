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
package com.exactpro.th2.schema.inframgr;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

public class Config {
    private static final String CONFIG_FILE = "config.yml";
    private static final String RABBITMQ_MANAGEMENT_CONFIG_FILE = "rabbitMQ-mng.json";
    private static final String CONFIG_DIR_SYSTEM_PROPERTY ="inframgr.config.dir";
    private static volatile Config instance;
    private Logger logger;
    private String configDir;

    private Config() {
        logger = LoggerFactory.getLogger(Config.class);
        configDir = System.getProperty(CONFIG_DIR_SYSTEM_PROPERTY, ".");
        configDir += "/";
    }

    private void parseFile(File file, ObjectMapper mapper, Object object) throws IOException {

        String fileContent = new String(Files.readAllBytes(file.toPath()));
        StringSubstitutor stringSubstitutor = new StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup());
        String enrichedContent = stringSubstitutor.replace(fileContent);
        mapper.readerForUpdating(object).readValue(enrichedContent);
    }

    private void readConfiguration() throws IOException {

        try {
            File file = new File(configDir + CONFIG_FILE);

            parseFile(file, new ObjectMapper(new YAMLFactory()), this);

            if (rabbitmq == null)
                rabbitmq = new RabbitMQConfig();

        } catch(UnrecognizedPropertyException e) {
            logger.error("Bad configuration: unknown property(\"{}\") specified in configuration file \"{}\""
                    , e.getPropertyName()
                    , CONFIG_FILE);
            throw new RuntimeException("Configuration exception", e);
        }
    }

    private void updateWithRabbitMQManagementSettings() throws IOException {

        try {
            File file = new File(configDir + RABBITMQ_MANAGEMENT_CONFIG_FILE);
            // back off safely as this configuration file is not mandatory
            if (!(file.exists() && file.isFile()))
                return;

            parseFile(file, new ObjectMapper(), this.getRabbitMQ());

        } catch(UnrecognizedPropertyException e) {
            logger.error("Bad configuration: unknown property(\"{}\") specified in configuration file \"{}\""
                    , e.getPropertyName()
                    , RABBITMQ_MANAGEMENT_CONFIG_FILE);
            throw new RuntimeException("Configuration exception", e);
        }
    }


    public static Config getInstance() throws IOException {
        if (instance == null) {
            synchronized (Config.class) {
                if (instance == null) {
                    Config config = new Config();
                    config.readConfiguration();
                    config.updateWithRabbitMQManagementSettings();

                    instance = config;
                }
            }
        }

        return instance;
    }


    public static class GitConfig {
        private  String remoteRepository;
        private boolean ignoreInsecureHosts;

        private  String localRepositoryRoot;

        private  String privateKeyFile;

        private  String privateKey;
        private  byte[] privateKeyBytes;

        public  String getRemoteRepository() {
            return remoteRepository;
        }

        public  void setRemoteRepository(String remoteRepository) {
            this.remoteRepository = remoteRepository;
        }

        public boolean ignoreInsecureHosts() {
            return ignoreInsecureHosts;
        }

        public void setIgnoreInsecureHosts(boolean ignoreInsecureHosts) {
            this.ignoreInsecureHosts = ignoreInsecureHosts;
        }

        public  String getLocalRepositoryRoot() {
            return localRepositoryRoot;
        }

        public  void setLocalRepositoryRoot(String localRepositoryRoot) {
            this.localRepositoryRoot = localRepositoryRoot;
        }

        public  String getPrivateKeyFile() {
            return privateKeyFile;
        }

        public  void setPrivateKeyFile(String privateKeyFile) {
            this.privateKeyFile = privateKeyFile;
        }

        public  byte[] getPrivateKey() {
            return privateKeyBytes;
        }

        public  void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
            this.privateKeyBytes = privateKey.getBytes();
        }
    }

    public static class RabbitMQConfig {
        private String host;
        private String port;
        private String username;
        private String password;
        private String vhostPrefix;

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

        public String getVhostPrefix() {
            return vhostPrefix;
        }

        public void setVhostPrefix(String vhostPrefix) {
            this.vhostPrefix = vhostPrefix;
        }
    }

    public static class CassandraConfig {
        private String host;
        private String port;
        private String username;
        private String password;
        private String keyspacePrefix;

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
            return keyspacePrefix;
        }

        public void setKeyspacePrefix(String keyspacePrefix) {
            this.keyspacePrefix = keyspacePrefix;
        }
    }

    public static class K8sConfig {
        private boolean useCustomConfig;
        private String masterURL;
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
            return namespacePrefix;
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

    private GitConfig git;
    public GitConfig getGit() {
        return git;
    }
    public void setGit(Config.GitConfig git) {
        this.git = git;
    }

    private K8sConfig kubernetes;
    public K8sConfig getKubernetes() {
        return kubernetes;
    }
    public void setKubernetes(K8sConfig kubernetes) {
        this.kubernetes = kubernetes;
    }

    private RabbitMQConfig rabbitmq;
    @JsonProperty("rabbitmq")
    public RabbitMQConfig getRabbitMQ() {
        return rabbitmq;
    }

    @JsonProperty("rabbitmq")
    public void setRabbitMQ(RabbitMQConfig rabbitmq) {
        this.rabbitmq = rabbitmq;
    }

    private CassandraConfig cassandra;
    public CassandraConfig getCassandra() {
        return cassandra;
    }
    public void setCassandra(CassandraConfig cassandra) {
        this.cassandra = cassandra;
    }
}
