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

package com.exactpro.th2.inframgr.metrics;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.util.cfg.PrometheusConfig;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class PrometheusServer {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusServer.class);

    private static final AtomicReference<HTTPServer> prometheusExporter = new AtomicReference<>();

    public static void start() throws IOException {
        DefaultExports.initialize();
        PrometheusConfig prometheusConfiguration = Config.getInstance().getPrometheusConfiguration();

        String host = prometheusConfiguration.getHost();
        int port = prometheusConfiguration.getPort();
        boolean enabled = prometheusConfiguration.getEnabled();

        prometheusExporter.updateAndGet(server -> {
            if (server == null && enabled) {
                try {
                    server = new HTTPServer(host, port);
                    logger.info("Started prometheus server on: \"{}:{}\"", host, port);
                    return server;
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create Prometheus exporter", e);
                }
            }
            return server;
        });
    }
}
