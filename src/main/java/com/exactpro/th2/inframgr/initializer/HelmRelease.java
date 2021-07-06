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

package com.exactpro.th2.inframgr.initializer;

import com.exactpro.th2.inframgr.FreeMarkerTemplate;
import com.exactpro.th2.inframgr.helmRelease.HrResource;
import com.exactpro.th2.inframgr.helmRelease.Spec;
import com.exactpro.th2.inframgr.k8s.K8sCustomResource;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.infrarepo.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class HelmRelease {

    private static final Logger logger = LoggerFactory.getLogger(HelmRelease.class);
    public static final String HELM_RELEASE_NAME = "th2-helm-test";
    public static final String TEMPLATE_NAME = "template.ftl";

    static void createOrReplaceHelmRelease(String schemaName, Kubernetes kube, boolean forceUpdate) throws IOException {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        HrResource newHelm = mapper.readValue(
                FreeMarkerTemplate.getHrTemplate(TEMPLATE_NAME, HELM_RELEASE_NAME, schemaName),
                HrResource.class);

        K8sCustomResource hr = kube.loadCustomResource(ResourceType.HelmRelease, HELM_RELEASE_NAME);
        if (hr != null) {
            try {
                Spec currSpec = mapper.convertValue(hr.getSpec(), Spec.class);

                if (newHelm.getSpec().getValues().getKeyspaceConfig().getSchemaVersion()
                        .equals(currSpec.getValues().getKeyspaceConfig().getSchemaVersion()) && !forceUpdate) {
                    logger.info("Helm release \"{}\" Not updated", HELM_RELEASE_NAME);
                    return;
                }
                logger.info("Updating helm release \"{}\"", HELM_RELEASE_NAME);
            } catch (Exception e) {
                logger.error("Exception updating helm release \"{}\"", HELM_RELEASE_NAME, e);
            }
        } else
            logger.info("Creating helm release \"{}\"", HELM_RELEASE_NAME);
        try {
            kube.createOrReplaceCustomResource(newHelm);
        } catch (Exception e) {
            logger.error("Exception creating helm release \"{}\"", HELM_RELEASE_NAME, e);
        }
    }
}
