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

import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.RegistryCredentialLookup;
import com.exactpro.th2.inframgr.docker.descriptor.DescriptorExtractor;
import com.exactpro.th2.inframgr.docker.descriptor.errors.BlobNotFoundException;
import com.exactpro.th2.inframgr.docker.descriptor.errors.ManifestNotFoundException;
import com.exactpro.th2.inframgr.errors.NotAcceptableException;
import com.exactpro.th2.inframgr.errors.ServiceException;
import com.exactpro.th2.inframgr.k8s.K8sCustomResource;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.infrarepo.ResourceType;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class DescriptorController {
    private static final String BAD_RESOURCE_NAME = "BAD_RESOURCE_NAME";
    private static final String REPOSITORY_ERROR = "REPOSITORY_ERROR";
    private static final String MANIFEST_ERROR = "MANIFEST_REQUEST_ERROR";
    private static final String BLOB_ERROR = "BLOB_REQUEST_ERROR";
    private static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";

    @GetMapping("/descriptor/{schema}/{kind}/{box}")
    @ResponseBody
    public String getDescriptor(@PathVariable(name = "schema") String schemaName,
                                @PathVariable(name = "kind") String kind,
                                @PathVariable(name = "box") String box
    ) throws ServiceException {
        if (!K8sCustomResource.isNameValid(schemaName)) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");
        }
        if (!K8sCustomResource.isNameValid(box)) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid resourceName");
        }
        if (ResourceType.forKind(kind) == null) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid kind");
        }


        DescriptorExtractor descriptorExtractor;
        try {
            Kubernetes kube = new Kubernetes(Config.getInstance().getKubernetes(), schemaName);
            RegistryCredentialLookup secretMapper = new RegistryCredentialLookup(kube);
            RegistryConnection registryConnection = new RegistryConnection(secretMapper.getCredentials());
            descriptorExtractor = new DescriptorExtractor(registryConnection, kube);
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }

        String descriptor;
        try {
            descriptor = descriptorExtractor.getImageDescriptor(kind, box);
        } catch (ResourceNotFoundException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.NOT_FOUND.name(), e.getMessage());
        } catch (ManifestNotFoundException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, MANIFEST_ERROR, e.getMessage());
        } catch (BlobNotFoundException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, BLOB_ERROR, e.getMessage());
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, e.getMessage());
        }
        return descriptor;
    }
}
