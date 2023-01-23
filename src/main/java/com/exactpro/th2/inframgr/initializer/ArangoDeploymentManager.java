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

import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.k8s.cr.ArangoDeployment;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import com.exactpro.th2.infrarepo.settings.ArangoDeploymentResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.exactpro.th2.inframgr.k8s.Kubernetes.KIND_ARANGO;

public class ArangoDeploymentManager {
    private static final Logger logger = LoggerFactory.getLogger(ArangoDeploymentManager.class);

    public static void synchronizeArangoDeployment(ArangoDeploymentResource arangoDeployment, Kubernetes kube) {
        String namespace = kube.getNamespaceName();
        String arangoDbName = arangoDeployment.getMetadata().getName();
        String resourceLabel = ResourcePath.annotationFor(namespace, KIND_ARANGO, arangoDbName);

        kube.createOrReplaceArangoDb(namespace, toK8sResource(arangoDeployment, namespace));
        logger.info("Synchronized \"{}\"", resourceLabel);
    }

    private static ArangoDeployment.Type toK8sResource(ArangoDeploymentResource arangoDeploymentResource,
                                                       String namespace) {
        ArangoDeployment.Type arangoDeployment = new ArangoDeployment.Type();
        arangoDeployment.setMetadata(arangoDeploymentResource.getMetadata());
        arangoDeployment.getMetadata().setNamespace(namespace);
        arangoDeployment.setKind(KIND_ARANGO);
        arangoDeployment.setSpec(arangoDeploymentResource.getSpec());
        return arangoDeployment;
    }
}
