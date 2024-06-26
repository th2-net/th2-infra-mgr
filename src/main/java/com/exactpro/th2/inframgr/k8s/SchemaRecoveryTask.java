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

package com.exactpro.th2.inframgr.k8s;

import com.exactpro.th2.inframgr.SchemaEvent;
import com.exactpro.th2.inframgr.SchemaEventRouter;
import com.exactpro.th2.inframgr.util.RetryableTask;
import io.fabric8.kubernetes.api.model.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class SchemaRecoveryTask implements RetryableTask {

    private static final Logger logger = LoggerFactory.getLogger(SchemaRecoveryTask.class);

    private static final int RETRY_DELAY_SEC = 60;

    private final Kubernetes schemaKube;

    private final int retryDelay;

    public SchemaRecoveryTask(Kubernetes schemaKube, int retryDelay) {
        Objects.requireNonNull(schemaKube.getSchemaName(), "Kubernetes client is anonymous");
        this.schemaKube = schemaKube;
        this.retryDelay = retryDelay;
    }

    public SchemaRecoveryTask(Kubernetes schemaKube) {
        this(schemaKube, RETRY_DELAY_SEC);
    }

    @Override
    public String getUniqueKey() {
        return SchemaRecoveryTask.class.getName() + ":" + schemaKube.getSchemaName();
    }

    @Override
    public long getRetryDelay() {
        return retryDelay;
    }

    @Override
    public void run() {
        // check actual state of the namespace
        try {
            Namespace namespace = schemaKube.getNamespace(schemaKube.getNamespaceName());

            if (namespace != null && !namespace.getStatus().getPhase().equals(Kubernetes.PHASE_ACTIVE)) {
                // namespace is still unavailable for operations
                // throw exception to trigger retry
                throw new IllegalStateException(
                        String.format(
                                "Cannot synchronize namespace \"%s\" as it is in the wrong state (%s)"
                                , namespace.getMetadata().getName()
                                , namespace.getStatus().getPhase()
                        )
                );
            }

            // namespace not found or is marked as active
            // send synchronization request
            SchemaEventRouter router = SchemaEventRouter.getInstance();
            SchemaEvent event = new SynchronizationRequestEvent(schemaKube.getSchemaName());
            router.addEvent(schemaKube.getSchemaName(), event);

        } catch (Exception e) {
            // rethrow exception to re-execute this task in the future
            logger.error("Exception recovering schema \"{}\", will be rescheduled for retry",
                    schemaKube.getSchemaName(), e);
            throw new RuntimeException(e);
        }
    }
}
