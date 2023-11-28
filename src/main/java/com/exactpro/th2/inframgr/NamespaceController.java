/*
 * Copyright 2023 Exactpro (Exactpro Systems Limited)
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
import com.exactpro.th2.inframgr.statuswatcher.Condition;
import com.exactpro.th2.inframgr.statuswatcher.ResourceCondition;
import com.exactpro.th2.inframgr.statuswatcher.StatusCache;
import com.exactpro.th2.infrarepo.git.Gitter;
import com.exactpro.th2.infrarepo.git.GitterContext;
import com.exactpro.th2.infrarepo.repo.Repository;
import com.exactpro.th2.infrarepo.settings.RepositorySettingsResource;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@SuppressWarnings("unused")
public class NamespaceController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NamespaceController.class);

    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";

    public static final String NAMESPACE_DOES_NOT_EXIST = "NAMESPACE_DOES_NOT_EXIST";

    public static final String GIT_BRANCH_IS_ACTIVE = "GIT_BRANCH_IS_ACTIVE";

    public static final String BAD_RESOURCE_NAME = "BAD_RESOURCE_NAME";

    @Autowired
    private StatusCache statusCache;

    @DeleteMapping("/namespace/{schemaName}")
    @ResponseBody
    public String getResourceDeploymentStatuses(HttpServletRequest request,
                                                @PathVariable(name = "schemaName") String schemaName) {

        try {
            // check schema name against valid pattern
            if (!K8sCustomResource.isSchemaNameValid(schemaName)) {
                throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");
            }

            Config config = Config.getInstance();
            String namespace = config.getKubernetes().getNamespacePrefix() + schemaName;
            LOGGER.debug("Checking namespace \"{}\"", namespace);
            try (KubernetesClient kubeClient = new KubernetesClientBuilder().build()) {
                Resource<Namespace> namespaceResource = kubeClient.namespaces().withName(namespace);
                if (namespaceResource == null) {
                    throw new ServiceException(HttpStatus.GONE, NAMESPACE_DOES_NOT_EXIST,
                            "Kube doesn't contain namespace \"" + namespace +
                                    "\" related to schema \"" + schemaName + "\"");
                }

                LOGGER.debug("Checking branch \"{}\"", schemaName);
                GitterContext ctx = GitterContext.getContext(config.getGit());
                Map<String, String> commits = ctx.getAllBranchesCommits();

                if (commits.containsKey(schemaName)) {
                    LOGGER.debug("Checking propagation for schema \"{}\"", schemaName);
                    Gitter gitter = ctx.getGitter(schemaName);
                    gitter.lock();
                    try {
                        RepositorySettingsResource repositorySettings = Repository.getSettings(gitter);
                        if (repositorySettings != null && !repositorySettings.getSpec().isK8sPropagationDenied()) {
                            throw new NotAcceptableException(GIT_BRANCH_IS_ACTIVE,
                                    "The schema \"" + schemaName + "\" is active, propagation \"" +
                                            repositorySettings.getSpec().getK8sPropagation() + "\"");
                        }
                    } finally {
                        gitter.unlock();
                    }
                }

                namespaceResource.delete();
                LOGGER.info("Deleted namespace \"{}\" related to the schema \"{}\", user: \"{}\"",
                        namespace, schemaName, request.getUserPrincipal().getName());
                return namespace;
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, UNKNOWN_ERROR,
                    "Exception deleting namespace related to schema " + schemaName + " from kuber", e);
        }
    }

    public static class ResponseEntry {

        @JsonProperty("kind")
        public String kind;

        @JsonProperty("name")
        public String name;

        @JsonProperty("conditions")
        public List<Condition> conditions;

        @JsonProperty("status")
        public String status;

        public ResponseEntry(ResourceCondition resource) {
            kind = resource.getKind();
            name = resource.getName();
            status = resource.getStatus().toString();
            if (resource.getConditions() != null) {
                conditions = new ArrayList<>(resource.getConditions().values());
            }
        }
    }
}
