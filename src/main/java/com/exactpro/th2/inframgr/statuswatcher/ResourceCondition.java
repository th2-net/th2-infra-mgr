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

package com.exactpro.th2.inframgr.statuswatcher;

import com.exactpro.th2.inframgr.k8s.K8sCustomResource;
import com.exactpro.th2.inframgr.repo.ResourceType;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResourceCondition {
    private static final String ANNOTATION_KEY = "th2.exactpro.com/antecedent";
    public static final String CONDITIONS = "conditions";
    public static final String PHASE = "phase";

    private String namespace;
    private String kind;
    private String name;

    private Map<String, Condition> conditions;
    private Map<String, String> annotations;
    private Status status;


    public String getNamespace() {
        return namespace;
    }

    public String getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public Status getStatus() {
        return status;
    }

    private void setStatus(Status status) {
        this.status = status;
    }

    public Map<String, Condition> getConditions() {
        return conditions;
    }

    public String getAntecedentAnnotation() {
        if (annotations != null && annotations.containsKey(ANNOTATION_KEY))
            return annotations.get(ANNOTATION_KEY);
        return  null;
    }


    public static ResourceCondition extractFrom(HasMetadata source) {

        ResourceCondition resource = new ResourceCondition();
        resource.namespace = source.getMetadata().getNamespace();
        resource.kind = source.getKind();
        resource.name = source.getMetadata().getName();
        resource.annotations = source.getMetadata().getAnnotations();
        resource.conditions = new HashMap<>();

        if (source instanceof Pod)
            processPod((Pod) source, resource);

        else if (source instanceof Deployment)
            processDeployment((Deployment) source, resource);

        else if (source instanceof K8sCustomResource) {
            if (source.getKind().equals("HelmRelease"))
                processHelmRelease((K8sCustomResource) source, resource);
            else
                processCustomResource((K8sCustomResource) source, resource);
        }
        else
            processResourceWithoutStatuses(source, resource);

        return resource;
    }


    private static void processPod(Pod pod, ResourceCondition resource) {
        Map<String, Condition> conditions = resource.getConditions();

        Status aggregatedStatus = null;
        for (ContainerStatus status : pod.getStatus().getContainerStatuses()) {
            ContainerState state = status.getState();
            if (state == null) {
                aggregatedStatus = Status.UNKNOWN;
                break;
            }

            if (state.getWaiting() != null)
                if (aggregatedStatus == null || aggregatedStatus.value > Status.PENDING.value) {
                    aggregatedStatus = Status.PENDING;
                    continue;
                }

            if (state.getTerminated() != null) {
                Status terminationStatus = Integer.valueOf(0).equals(state.getTerminated().getExitCode()) ? Status.COMPLETE : Status.FAILED;
                if (aggregatedStatus == null || aggregatedStatus.value > terminationStatus.value)
                    aggregatedStatus = terminationStatus;
                break;
            }

            if (state.getRunning() != null)
                if (aggregatedStatus == null || aggregatedStatus.value > Status.RUNNING.value) {
                    aggregatedStatus = Status.RUNNING;
                    continue;
                }

            aggregatedStatus = Status.UNKNOWN;
            break;
        }

        if (aggregatedStatus == null)
            aggregatedStatus = Status.UNKNOWN;
        resource.setStatus(aggregatedStatus);

        if (pod.getStatus() != null && pod.getStatus().getConditions() != null) {
            for (PodCondition pc: pod.getStatus().getConditions())
                if (pc != null) {
                    Condition c = new Condition();
                    c.setType(pc.getType());
                    c.setStatus(pc.getStatus());
                    c.setMessage(pc.getMessage());
                    conditions.put(c.getType(), c);
                }
        }
    }


    private static void processDeployment(Deployment deployment, ResourceCondition resource) {
        Map<String, Condition> conditions = resource.getConditions();
        if (deployment.getStatus() != null && deployment.getStatus().getConditions() != null) {
            for (DeploymentCondition dc: deployment.getStatus().getConditions())
                if (dc != null) {
                    Condition c = new Condition();
                    c.setType(dc.getType());
                    c.setStatus(dc.getStatus());
                    c.setMessage(dc.getMessage());
                    conditions.put(c.getType(), c);
                }
        }

        Condition available = conditions.get("Available");
        if (available != null && Condition.STATUS_TRUE.equals(available.getStatus())) {
            resource.setStatus(Status.RUNNING);
            return;
        }

        Condition progressing = conditions.get("Progressing");
        if (progressing != null) {
            if (Condition.STATUS_TRUE.equals(progressing.getStatus()))
                resource.setStatus(Status.PENDING);
            else if (Condition.STATUS_FALSE.equals(progressing.getStatus()))
                resource.setStatus(Status.FAILED);
            else
                resource.setStatus(Status.UNKNOWN);
            return;
        }
        resource.setStatus(Status.UNKNOWN);
    }


    private static void processResourceWithoutStatuses(HasMetadata source, ResourceCondition resource) {
        resource.setStatus(Status.RUNNING);
    }


    private static void processHelmRelease(K8sCustomResource customResource, ResourceCondition resource) {
        Map<String, Condition> conditions = resource.getConditions();
        resource.setStatus(Status.UNKNOWN);

        if (customResource.getStatus() != null && customResource.getStatus() instanceof Map) {
            Map<String, Object> status = (Map) customResource.getStatus();

            if (status.containsKey(CONDITIONS)) {
                List crConditions = (List) ((Map) customResource.getStatus()).get(CONDITIONS);
                for (var crc : crConditions)
                    if (crc instanceof Map) {
                        Condition c = new Condition();
                        c.setType(safeToString(((Map<String, ?>) crc).get("type")));
                        c.setStatus(safeToString(((Map<String, ?>) crc).get("status")));
                        c.setMessage(safeToString(((Map<String, ?>) crc).get("message")));
                        conditions.put(c.getType(), c);
                    }
            }

            Object phase = status.get(PHASE);
            if ( phase != null)
                switch (phase.toString()) {

                    case "Succeeded":
                        resource.setStatus(Status.RUNNING);
                        break;

                    case "ChartFetched":
                    case "Installing":
                    case "Upgrading":
                    case "Testing":
                    case "Tested":
                    case "RollingBack":
                    case "Deployed":
                        resource.setStatus(Status.PENDING);
                        break;

                    case "ChartFetchFailed":
                    case "DeployFailed":
                    case "TestFailed":
                    case "Failed":
                    case "RollbackFailed":
                        resource.setStatus(Status.FAILED);
                        break;
                }
        }
    }


    private static void processCustomResource(K8sCustomResource customResource, ResourceCondition resource) {

        String kind = resource.getKind();
        if (kind.equals(ResourceType.Th2Link.kind()) || kind.equals(ResourceType.Th2Dictionaries.kind())) {
            // these custom resources are considered succesfully deployed
            resource.setStatus(Status.RUNNING);
            return;
        }

        Map<String, Condition> conditions = resource.getConditions();
        if (customResource.getStatus() != null && customResource.getStatus() instanceof Map) {
            Map<String, Object> status = (Map) customResource.getStatus();

            if (status.containsKey(CONDITIONS)) {
                List crConditions = (List) ((Map) customResource.getStatus()).get(CONDITIONS);
                for (var crc : crConditions)
                    if (crc instanceof Map) {
                        Condition c = new Condition();
                        c.setType(safeToString(((Map<String, ?>) crc).get("type")));
                        c.setStatus(safeToString(((Map<String, ?>) crc).get("status")));
                        c.setMessage(safeToString(((Map<String, ?>) crc).get("message")));
                        conditions.put(c.getType(), c);
                    }
            }

            Object phase = status.get(PHASE);
            if (phase != null) {
                switch (phase.toString()) {
                    case "Failed":
                        resource.setStatus(Status.FAILED);
                        break;
                    case "Succeeded":
                        resource.setStatus(Status.RUNNING);
                        break;
                    case "Idle":
                    case "Installing":
                    case "Upgrading":
                    case "Deleting":
                        resource.setStatus(Status.PENDING);
                }
            }
        }
        if (resource.getStatus() == null)
            resource.setStatus(Status.UNKNOWN);
    }


    private static String safeToString(Object o) {
        return o == null ? null : o.toString();
    }

    public enum Status {
        COMPLETE(3, "Complete"),
        RUNNING(2, "Running"),
        PENDING(1, "Pending"),
        UNKNOWN(0, "Unknown"),
        FAILED(-1, "Failed");

        private int value;
        private String text;
        Status(int value, String text) {
            this.value = value;
            this.text = text;
        }
        public int value() {
            return value;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
