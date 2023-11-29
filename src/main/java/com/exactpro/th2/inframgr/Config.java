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

import com.exactpro.th2.inframgr.util.cfg.BehaviourCfg;
import com.exactpro.th2.inframgr.util.cfg.CassandraConfig;
import com.exactpro.th2.inframgr.util.cfg.GitCfg;
import com.exactpro.th2.inframgr.util.cfg.HttpCfg;
import com.exactpro.th2.inframgr.util.cfg.K8sConfig;
import com.exactpro.th2.inframgr.util.cfg.PrometheusConfig;
import com.exactpro.th2.inframgr.util.cfg.RabbitMQConfig;
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
import java.nio.file.Path;

// TODO: instant this class as spring @Bean instead of singleton
public class Config {

    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);

    private static final String CONFIG_FILE = "config.yml";

    private static final String CONFIG_DIR_SYSTEM_PROPERTY = "inframgr.config.dir";

    private static volatile Config instance;

    private static final Path CONFIG_DIR = Path.of(System.getProperty(CONFIG_DIR_SYSTEM_PROPERTY, "."));

    // config fields
    private BehaviourCfg behaviour;

    private HttpCfg http;

    private GitCfg git;

    private RabbitMQConfig rabbitmq;

    private CassandraConfig cassandra;

    private PrometheusConfig prometheusConfiguration;

    private K8sConfig kubernetes;

    public BehaviourCfg getBehaviour() {
        return behaviour;
    }

    public HttpCfg getHttp() {
        return http;
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

    public void setHttp(HttpCfg http) {
        this.http = http;
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

    private Config() {}

    public static Config getInstance() throws IOException {
        if (instance == null) {
            synchronized (Config.class) {
                if (instance == null) {
                    Path file = CONFIG_DIR.resolve(CONFIG_FILE);
                    instance = readConfiguration(file);
                }
            }
        }

        return instance;
    }

    private static void parseFile(File file, ObjectMapper mapper, Object object) throws IOException {

        String fileContent = new String(Files.readAllBytes(file.toPath()));
        StringSubstitutor stringSubstitutor = new StringSubstitutor(
                StringLookupFactory.INSTANCE.environmentVariableStringLookup()
        );
        String enrichedContent = stringSubstitutor.replace(fileContent);
        mapper.readerForUpdating(object).readValue(enrichedContent);
    }

    static Config readConfiguration(Path configFile) throws IOException {
        try {
            Config config = new Config();
            parseFile(configFile.toFile(), new ObjectMapper(new YAMLFactory()).enable(
                            JsonParser.Feature.STRICT_DUPLICATE_DETECTION).
                    registerModule(new KotlinModule.Builder().build()), config);

            if (config.getRabbitMQ() == null) {
                config.setRabbitMQ(new RabbitMQConfig());
            }
            if (config.getBehaviour() == null) {
                config.setBehaviour(new BehaviourCfg());
            }
            if (config.getCassandra() == null) {
                config.setCassandra(new CassandraConfig());
            }
            if (config.getHttp() == null) {
                throw new IllegalStateException("'http' config can't be null");
            }

            config.getHttp().validate();

            return config;
        } catch (UnrecognizedPropertyException e) {
            LOGGER.error("Bad configuration: unknown property(\"{}\") specified in configuration file \"{}\""
                    , e.getPropertyName()
                    , CONFIG_FILE);
            throw new RuntimeException("Configuration exception", e);
        } catch (JsonParseException e) {
            LOGGER.error("Bad configuration: exception while parsing configuration file \"{}\""
                    , CONFIG_FILE);
            throw new RuntimeException("Configuration exception", e);
        }
    }
}
