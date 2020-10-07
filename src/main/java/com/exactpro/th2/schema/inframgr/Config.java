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

import com.exactpro.th2.schema.inframgr.util.cfg._CassandraConfig;
import com.exactpro.th2.schema.inframgr.util.cfg._GitConfig;
import com.exactpro.th2.schema.inframgr.util.cfg._K8sConfig;
import com.exactpro.th2.schema.inframgr.util.cfg._RabbitMQConfig;
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

    public static class GitConfig  extends _GitConfig {}
    public static class RabbitMQConfig extends _RabbitMQConfig {}
    public static class CassandraConfig extends _CassandraConfig {}
    public static class K8sConfig extends _K8sConfig {}
}
