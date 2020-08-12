package com.exactpro.th2.schema.schemaeditorbe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Config {
    public static final String CONFIG_FILE = "config.yml";
    private static Config instance;

    private Config() throws Exception {
    }

    private static void readConfiguration()  throws IOException {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        StringSubstitutor stringSubstitutor = new StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup());

        File configFile = new File(CONFIG_FILE);
        String contents = stringSubstitutor.replace(new String(Files.readAllBytes(configFile.toPath())));
        mapper.readerForUpdating(instance).readValue(contents);
    }

    public static Config getInstance() throws Exception {
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


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitConfig {
        private  String remoteRepository;

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

    public GitConfig getGit() {
        return git;
    }

    public void setGit(Config.GitConfig git) {
        this.git = git;
    }

    private GitConfig git;
}
