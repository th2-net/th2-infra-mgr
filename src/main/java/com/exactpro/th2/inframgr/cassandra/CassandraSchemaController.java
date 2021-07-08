package com.exactpro.th2.inframgr.cassandra;

import com.exactpro.th2.inframgr.InfraManagerApplication;
import com.exactpro.th2.inframgr.cassandra.jobs.GracefulMigrationMonitoringTask;
import com.exactpro.th2.inframgr.cassandra.jobs.SchemaCreationMonitoringJob;
import com.exactpro.th2.inframgr.cassandra.template.FreeMarkerTemplate;
import com.exactpro.th2.inframgr.cassandra.template.helmrelease.HrResource;
import com.exactpro.th2.inframgr.initializer.SchemaInitializer;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.util.RetryableTaskQueue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraSchemaController {
    private static final Logger logger = LoggerFactory.getLogger(CassandraSchemaController.class);

    private static final int THREAD_COUNT = 3;
    private static final String GRACEFUL_MIGRATION_JOB_IMAGE_NAME = "graceful-migration-job";
    private static final String GRACEFUL_MIGRATION_JOB_IMAGE_VERSION = "1.2.3";

    private static final String FORCED_MIGRATION_JOB_IMAGE_NAME = "forced-migration-job";
    private static final String FORCED_MIGRATION_JOB_IMAGE_VERSION = "1.2.3";

    private static final String SCHEMA_CREATION_JOB_IMAGE_NAME = "schema-creation-job";
    private static final String SCHEMA_CREATION_JOB_IMAGE_VERSION = "1.2.3";

    private static final String HELM_RELEASE_NAME = "th2-helm-test";
    private static final String TEMPLATE_NAME = "template.ftl";

    private static RetryableTaskQueue taskQueue = new RetryableTaskQueue(THREAD_COUNT);

    public static void process(String schemaName, Kubernetes kube) throws JsonProcessingException {
        var currentSchema = getCurrentSchema(schemaName);
        if (currentSchema == null) {
            createCassandraSchema(schemaName, kube);
        } else if (schemaMigrationRequired(schemaName, currentSchema)) {
            if (schemaMigrationInProgress(currentSchema)) {

            } else {
                startGracefulMigration(schemaName, kube);
            }
        }
    }

    public static boolean schemaMigrationRequired(String schemaName, InfraManagerApplication.CassandraSchema currentSchema) {
        String newSchemaVersion = SchemaInitializer.getKeyspaceMap().get(schemaName).getSchemaVersion();
        if (newSchemaVersion.isEmpty()) {
            return false;
        }
        return newSchemaVersion.equals(currentSchema.getSchemaVersion());
    }

    public static boolean schemaMigrationInProgress(InfraManagerApplication.CassandraSchema currentSchema) {
        return currentSchema.getKeyspaceStatus().equals(InfraManagerApplication.KeyspaceStatus.IN_PROGRESS);
    }

    public static void startGracefulMigration(String schemaName, Kubernetes kube) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        HrResource newHelm = mapper.readValue(
                FreeMarkerTemplate.getHrTemplate(TEMPLATE_NAME, HELM_RELEASE_NAME, schemaName, GRACEFUL_MIGRATION_JOB_IMAGE_NAME, GRACEFUL_MIGRATION_JOB_IMAGE_VERSION),
                HrResource.class);
        try {
            kube.createOrReplaceCustomResource(newHelm);
            taskQueue.add(new GracefulMigrationMonitoringTask(schemaName, newHelm.getMetadata().getName(), kube), true);
        } catch (Exception e) {
            logger.error("Exception updating helm release \"{}\"", HELM_RELEASE_NAME, e);
        }
    }

    public static void createCassandraSchema(String schemaName, Kubernetes kube) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        HrResource newHelm = mapper.readValue(
                FreeMarkerTemplate.getHrTemplate(TEMPLATE_NAME, HELM_RELEASE_NAME, schemaName, SCHEMA_CREATION_JOB_IMAGE_NAME, SCHEMA_CREATION_JOB_IMAGE_VERSION),
                HrResource.class);
        try {
            kube.createOrReplaceCustomResource(newHelm);
            taskQueue.add(new SchemaCreationMonitoringJob(), true);
        } catch (Exception e) {
            logger.error("Exception updating helm release \"{}\"", HELM_RELEASE_NAME, e);
        }
    }

    private static InfraManagerApplication.CassandraSchema getCurrentSchema(String schemaName) {
        Object cassandraSchemaResponse = InfraManagerApplication.CASSANDRA.get(schemaName);
        if (cassandraSchemaResponse == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(cassandraSchemaResponse, InfraManagerApplication.CassandraSchema.class);
    }
}
