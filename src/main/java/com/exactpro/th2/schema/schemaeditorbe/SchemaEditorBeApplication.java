package com.exactpro.th2.schema.schemaeditorbe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SchemaEditorBeApplication {

	public static void main(String[] args) {

		try {
			// force load configuration
			Config.getInstance();

			SpringApplication.run(SchemaEditorBeApplication.class, args);
		} catch (Exception e) {
			Logger logger = LoggerFactory.getLogger(SchemaEditorBeApplication.class);
			logger.error("exiting with exception", e);
		}
	}

}
