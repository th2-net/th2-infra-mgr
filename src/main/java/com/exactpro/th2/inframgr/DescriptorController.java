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
import com.exactpro.th2.inframgr.docker.descriptor.errors.RegistryRequestException;
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

import javax.servlet.http.HttpServletResponse;


@Controller
public class DescriptorController {
    private static final String PROTOBUF_DESCRIPTOR = "protobuf-description-base64";

    private static final String BAD_RESOURCE_NAME = "BAD_RESOURCE_NAME";
    private static final String REGISTRY_ERROR = "DOCKER_REGISTRY_ERROR";
    private static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";

    @GetMapping("/descriptor/{schema}/{kind}/{box}")
    @ResponseBody
    public Response getDescriptor(@PathVariable(name = "schema") String schemaName,
                                  @PathVariable(name = "kind") String kind,
                                  @PathVariable(name = "box") String box,
                                  HttpServletResponse response

    ) throws ServiceException {
        if (!K8sCustomResource.isNameValid(schemaName)) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");
        }
        if (!K8sCustomResource.isNameValid(box)) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid resourceName");
        }
        if (ResourceType.forKind(kind) == null) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid resource kind");
        }

        String descriptor;
        try {
            Kubernetes kube = new Kubernetes(Config.getInstance().getKubernetes(), schemaName);
            RegistryCredentialLookup secretMapper = new RegistryCredentialLookup(kube);
            RegistryConnection registryConnection = new RegistryConnection(secretMapper.getCredentials());
            DescriptorExtractor descriptorExtractor = new DescriptorExtractor(registryConnection, kube);
            descriptor = descriptorExtractor.getImageDescriptor(kind, box, PROTOBUF_DESCRIPTOR);
        } catch (ResourceNotFoundException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.NOT_FOUND.name(), e.getMessage());
        } catch (RegistryRequestException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REGISTRY_ERROR, e.getMessage());
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, e.getMessage());
        }
        if (descriptor != null) {
            return new Response(PROTOBUF_DESCRIPTOR, descriptor);
        }
        response.setStatus(HttpStatus.NO_CONTENT.value());
        return null;
    }

    public class Response {
        private final String descriptor;
        private final String content;

        public Response(String descriptor, String content) {
            this.descriptor = descriptor;
            this.content = content;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public String getContent() {
            return content;
        }
    }

}
