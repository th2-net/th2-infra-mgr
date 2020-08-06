package com.exactpro.th2.schema.schemaeditorbe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.net.URL;

public class Config {
    private static Config instance;

    private Config() throws Exception {
    }

    private static void readConfiguration()  throws IOException {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.readerForUpdating(instance).readValue(new URL("file:config.yml"));
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
        private static String remoteRepository;

        private String localRepositoryRoot;

        private String privateKeyFile;

        public static String getRemoteRepository() {
            return remoteRepository;
        }

        public void setRemoteRepository(String remoteRepository) {
            this.remoteRepository = remoteRepository;
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

        public static void setPrivateKeyFile(String privateKeyFile) {
            privateKeyFile = privateKeyFile;
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
