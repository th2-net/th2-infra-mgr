package com.exactpro.th2.schema.schemaeditorbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SchemaEditorBeApplication {

	public static void main(String[] args) throws Exception {

		// load configuration
		Config.getInstance();
		SpringApplication.run(SchemaEditorBeApplication.class, args);
	}

}
