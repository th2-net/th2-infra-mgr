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

package com.exactpro.th2.inframgr;

import com.exactpro.th2.inframgr.metrics.PrometheusServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;

@SpringBootApplication
@EnableScheduling
public class InfraManagerApplication {

    public static final Map<String, Object> CASSANDRA = Map.of(
    		"poc", new CassandraSchema(KeyspaceStatus.IN_PROGRESS, "1.0.0.")
	);

    public static final class CassandraSchema {
        private KeyspaceStatus keyspaceStatus;
        private String schemaVersion;

        public CassandraSchema(KeyspaceStatus keyspaceStatus, String schemaVersion) {
            this.keyspaceStatus = keyspaceStatus;
            this.schemaVersion = schemaVersion;
        }

		public KeyspaceStatus getKeyspaceStatus() {
			return keyspaceStatus;
		}

		public String getSchemaVersion() {
			return schemaVersion;
		}
	}

    public enum KeyspaceStatus {
        IN_PROGRESS,
        FINISHED,
        FAILED
    }

    public static void main(String[] args) {

        try {
            // preload configuration
            Config.getInstance();

            PrometheusServer.start();
            SpringApplication application = new SpringApplication(InfraManagerApplication.class);
            application.run(args);

        } catch (Exception e) {
            Logger logger = LoggerFactory.getLogger(InfraManagerApplication.class);
            logger.error("Exiting with exception", e);
        }
    }
}
