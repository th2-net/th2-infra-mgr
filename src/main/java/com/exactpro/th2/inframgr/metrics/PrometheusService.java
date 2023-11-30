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

package com.exactpro.th2.inframgr.metrics;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.util.cfg.PrometheusConfig;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

@Component
// TODO: replace to spring implementation
public class PrometheusService {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusService.class);

    private static final AtomicReference<HTTPServer> prometheusExporter = new AtomicReference<>();

    @Autowired
    private Config config;

    @PostConstruct
    public void start() {
        DefaultExports.initialize();
        PrometheusConfig prometheusConfiguration = config.getPrometheusConfiguration();

        if (prometheusConfiguration.getEnabled()) {
            try {
                String host = prometheusConfiguration.getHost();
                int port = prometheusConfiguration.getPort();
                prometheusExporter.set(new HTTPServer(host, port));
                logger.info("Started prometheus server on: \"{}:{}\"", host, port);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create Prometheus exporter", e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        HTTPServer httpServer = prometheusExporter.get();
        if (httpServer != null) {
            httpServer.close();
        }
    }
}
