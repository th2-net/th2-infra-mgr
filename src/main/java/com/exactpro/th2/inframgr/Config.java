/*
 * Copyright 2020-2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.inframgr;

import com.exactpro.th2.inframgr.util.cfg.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Config {

    private static final String CONFIG_FILE = "config.yml";

    private static final String CONFIG_DIR_SYSTEM_PROPERTY = "inframgr.config.dir";

    private static volatile Config instance;

    private final Logger logger;

    private String configDir;

    // config fields
    private BehaviourCfg behaviour;

    private GitCfg git;

    private RabbitMQConfig rabbitmq;

    private CassandraConfig cassandra;

    private PrometheusConfig prometheusConfiguration;

    private K8sConfig kubernetes;

    public BehaviourCfg getBehaviour() {
        return behaviour;
    }

    public GitCfg getGit() {
        return git;
    }

    @JsonProperty("rabbitmq")
    public RabbitMQConfig getRabbitMQ() {
        return rabbitmq;
    }

    public CassandraConfig getCassandra() {
        return cassandra;
    }

    public PrometheusConfig getPrometheusConfiguration() {
        return prometheusConfiguration;
    }

    public K8sConfig getKubernetes() {
        return kubernetes;
    }

    public void setBehaviour(BehaviourCfg behaviour) {
        this.behaviour = behaviour;
    }

    public void setGit(GitCfg git) {
        this.git = git;
    }

    @JsonProperty("rabbitmq")
    public void setRabbitMQ(RabbitMQConfig rabbitmq) {
        this.rabbitmq = rabbitmq;
    }

    public void setCassandra(CassandraConfig cassandra) {
        this.cassandra = cassandra;
    }

    public void setPrometheusConfiguration(PrometheusConfig prometheusConfiguration) {
        this.prometheusConfiguration = prometheusConfiguration;
    }

    public void setKubernetes(K8sConfig kubernetes) {
        this.kubernetes = kubernetes;
    }

    private Config() {
        logger = LoggerFactory.getLogger(Config.class);
        configDir = System.getProperty(CONFIG_DIR_SYSTEM_PROPERTY, ".");
        configDir += "/";
    }

    private void parseFile(File file, ObjectMapper mapper, Object object) throws IOException {

        String fileContent = new String(Files.readAllBytes(file.toPath()));
        StringSubstitutor stringSubstitutor = new StringSubstitutor(
                StringLookupFactory.INSTANCE.environmentVariableStringLookup()
        );
        String enrichedContent = stringSubstitutor.replace(fileContent);
        mapper.readerForUpdating(object).readValue(enrichedContent);
    }

    private void readConfiguration() throws IOException {

        try {
            File file = new File(configDir + CONFIG_FILE);

            parseFile(file, new ObjectMapper(new YAMLFactory()).enable(
                    JsonParser.Feature.STRICT_DUPLICATE_DETECTION).
                    registerModule(new KotlinModule.Builder().build()), this);

            if (rabbitmq == null) {
                rabbitmq = new RabbitMQConfig();
            }

        } catch (UnrecognizedPropertyException e) {
            logger.error("Bad configuration: unknown property(\"{}\") specified in configuration file \"{}\""
                    , e.getPropertyName()
                    , CONFIG_FILE);
            throw new RuntimeException("Configuration exception", e);
        } catch (JsonParseException e) {
            logger.error("Bad configuration: exception while parsing configuration file \"{}\""
                    , CONFIG_FILE);
            throw new RuntimeException("Configuration exception", e);
        }
    }

    public static Config getInstance() throws IOException {
        if (instance == null) {
            synchronized (Config.class) {
                if (instance == null) {
                    Config config = new Config();
                    config.readConfiguration();

                    instance = config;
                }
            }
        }

        return instance;
    }
}
