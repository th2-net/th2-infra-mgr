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

import com.exactpro.th2.inframgr.errors.NotAcceptableException;
import com.exactpro.th2.inframgr.errors.ServiceException;
import com.exactpro.th2.inframgr.k8s.K8sCustomResource;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.infrarepo.*;
import com.exactpro.th2.infrarepo.git.Gitter;
import com.exactpro.th2.infrarepo.git.GitterContext;
import com.exactpro.th2.infrarepo.repo.Repository;
import com.exactpro.th2.infrarepo.repo.RepositoryResource;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

import static com.exactpro.th2.inframgr.statuswatcher.ResourcePath.annotationFor;

@RestController
public class JobController {

    private static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";

    private static final String GIT_ERROR = "GIT_ERROR";

    private static final String CONFIG_ERROR = "GIT_ERROR";

    private static final String BAD_RESOURCE_NAME = "BAD_RESOURCE_NAME";

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    @PutMapping("/jobs/{schemaName}/{jobName}")
    public void putSecrets(@PathVariable(name = "schemaName") String schemaName,
                           @PathVariable(name = "jobName") String jobName) {
        logger.debug("received request for job creation, job name: {}", jobName);
        if (!K8sCustomResource.isSchemaNameValid(schemaName)) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");
        }
        if (!K8sCustomResource.isNameValid(jobName)) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid resource name");
        }

        try (Kubernetes kube = new Kubernetes(Config.getInstance().getKubernetes(), schemaName)) {
            RepositoryResource resource;
            String resourceLabel;
            GitterContext ctx = GitterContext.getContext(Config.getInstance().getGit());
            Gitter gitter = ctx.getGitter(schemaName);
            try {
                gitter.lock();
                gitter.checkout();
                resource = Repository.getResource(gitter, ResourceType.Th2Job.kind(), jobName);
                resourceLabel = annotationFor(kube.getNamespaceName(), ResourceType.Th2Job.kind(), jobName);
            } catch (GitAPIException e) {
                throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, GIT_ERROR, e.getMessage());
            } finally {
                gitter.unlock();
            }
            kube.deleteCustomResource(resource);
            logger.info("Delete resource : {}", resourceLabel);
            kube.createCustomResource(resource);
            logger.info("Created job with name : {}", resourceLabel);
        } catch (IOException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, CONFIG_ERROR, e.getMessage());
        }
    }
}
