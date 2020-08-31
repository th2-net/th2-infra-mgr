package com.exactpro.th2.schema.schemaeditorbe;

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
import java.util.Set;

public class Config {
    public static final String CONFIG_FILE = "config.yml";
    private static volatile Config instance;

    private Config() {
    }

    private static void readConfiguration() throws IOException {

        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            StringSubstitutor stringSubstitutor = new StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup());

            File configFile = new File(CONFIG_FILE);
            String contents = stringSubstitutor.replace(new String(Files.readAllBytes(configFile.toPath())));
            mapper.readerForUpdating(instance).readValue(contents);

        } catch(UnrecognizedPropertyException e) {
            Logger logger = LoggerFactory.getLogger(Config.class);
            logger.error("bad configuration: unknown property(\"{}\") specified in configuration file", e.getPropertyName());
            throw new RuntimeException("Configuration exception", e);
        }
    }

    public static Config getInstance() throws IOException {
        if (instance == null) {
            synchronized (Config.class) {
                if (instance == null) {
                    instance = new Config();
                    readConfiguration();
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

    public static class K8sConfig {
        private boolean useCustomConfig;
        private String masterURL;
        private String apiVersion;
        private boolean ignoreInsecureHosts;
        private String clientCertificateFile;
        private String clientKeyFile;
        private String clientCertificate;
        private String clientKey;
        private Set<String> secretNames;

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
    }

    public GitConfig getGit() {
        return git;
    }

    public void setGit(Config.GitConfig git) {
        this.git = git;
    }

    public K8sConfig getKubernetes() {
        return kubernetes;
    }

    public void setKubernetes(K8sConfig kubernetes) {
        this.kubernetes = kubernetes;
    }

    private GitConfig git;
    private K8sConfig kubernetes;
}
