/*
 * Copyright 2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.inframgr.k8s;

import com.exactpro.th2.inframgr.Config;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class KubernetesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesService.class);

    private final ConcurrentMap<String, Kubernetes> clients = new ConcurrentHashMap<>();

    private Kubernetes defaultClient;

    @Autowired
    private Config config;

    public Kubernetes getKubernetes(String schema) {
        if (schema == null) {
            return defaultClient;
        }
        return clients.computeIfAbsent(schema, key -> {
            LOGGER.info("Creating kubernetes client for the schema '{}'", key);
            return new Kubernetes(config.getBehaviour(), config.getKubernetes(), key);
        });
    }

    public Kubernetes getKubernetes() {
        return getKubernetes(null);
    }

    @PostConstruct
    private void postConstruct() {
        LOGGER.info("Initialising kubernetes controller");
        defaultClient = new Kubernetes(config.getBehaviour(), config.getKubernetes(), null);
    }

    @PreDestroy
    private void preDestroy() {
        LOGGER.info("Closing kubernetes clients");
        try {
            defaultClient.close();
        } catch (Exception e) {
            LOGGER.error("Closing default kubernetes client failure", e);
        }

        for (String schema : clients.keySet()) {
            try {
                Kubernetes client = clients.remove(schema);
                if (client == null) {
                    LOGGER.warn("kubernetes client for schema '{}' is already removed", schema);
                } else {
                    client.close();
                }
            } catch (Exception e) {
                LOGGER.error("Closing kubernetes client for schema '{}' failure", schema, e);
            }
        }
    }
}
