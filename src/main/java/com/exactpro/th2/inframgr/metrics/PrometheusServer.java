package com.exactpro.th2.inframgr.metrics;

import com.exactpro.th2.inframgr.Config;
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
        Config.PrometheusConfiguration prometheusConfiguration = Config.getInstance().getPrometheusConfiguration();

        String host = prometheusConfiguration.getHost();
        int port = prometheusConfiguration.getPort();
        boolean enabled = prometheusConfiguration.isEnabled();

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
