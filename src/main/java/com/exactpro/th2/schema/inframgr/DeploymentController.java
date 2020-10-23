/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.schema.inframgr;

import com.exactpro.th2.schema.inframgr.errors.NotAcceptableException;
import com.exactpro.th2.schema.inframgr.errors.ServiceException;
import com.exactpro.th2.schema.inframgr.k8s.K8sCustomResource;
import com.exactpro.th2.schema.inframgr.statuswatcher.Condition;
import com.exactpro.th2.schema.inframgr.statuswatcher.ResourceCondition;
import com.exactpro.th2.schema.inframgr.statuswatcher.StatusCache;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Controller
public class DeploymentController {
    private static final Logger logger = LoggerFactory.getLogger(DeploymentController.class);
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
    public static final String BAD_RESOURCE_NAME = "BAD_RESOURCE_NAME";

    @Autowired
    private StatusCache statusCache;

    @GetMapping("/deployment/{schema}/{kind}/{resource}/status")
    @ResponseBody
    public List<ResponseEntry> getResourceDeploymentStatuses(
            @PathVariable(name="schema") String schemaName,
            @PathVariable(name="kind") String kind,
            @PathVariable(name="resource") String resourceName) {

        try {
            // check schema name against valid pattern
            Pattern pattern = Pattern.compile(K8sCustomResource.RESOURCE_NAME_REGEXP);
            if (!pattern.matcher(schemaName).matches())
                throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");

            List<ResponseEntry> response = new ArrayList<>();
            for (ResourceCondition resource: statusCache.getResourceDependencyStatuses(schemaName, kind, resourceName))
                response.add(new ResponseEntry(resource));

            return response;

        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Exception retrieving schema {} from repository", schemaName, e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, e.getMessage());
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
            if (resource.getConditions() != null)
                conditions = new ArrayList<>(resource.getConditions().values());
        }
    }
}