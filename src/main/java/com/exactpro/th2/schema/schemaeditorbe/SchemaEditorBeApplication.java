package com.exactpro.th2.schema.schemaeditorbe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SchemaEditorBeApplication {

	public static void main(String[] args) {

		try {
			// preload configuration
			Config.getInstance();

			SpringApplication application = new SpringApplication(SchemaEditorBeApplication.class);
			application.run(args);

		} catch (Exception e) {
			Logger logger = LoggerFactory.getLogger(SchemaEditorBeApplication.class);
			logger.error("Exiting with exception ({})", e.getMessage());
		}
	}
}
