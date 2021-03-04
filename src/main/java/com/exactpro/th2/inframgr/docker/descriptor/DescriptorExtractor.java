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

package com.exactpro.th2.inframgr.docker.descriptor;

import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.descriptor.errors.BlobNotFoundException;
import com.exactpro.th2.inframgr.docker.descriptor.errors.ManifestNotFoundException;
import com.exactpro.th2.inframgr.docker.model.schemav2.Blob;
import com.exactpro.th2.inframgr.docker.model.schemav2.ImageManifestV2;
import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import com.exactpro.th2.inframgr.k8s.K8sCustomResource;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import com.exactpro.th2.infrarepo.ResourceType;
import io.fabric8.kubernetes.client.ResourceNotFoundException;

import java.util.Map;

public class DescriptorExtractor {
    private static final String DESCRIPTOR_ALIAS = "protobuf-description-base64";

    private final Kubernetes kube;

    RegistryConnection connection;

    public DescriptorExtractor(RegistryConnection connection, Kubernetes kube) {
        this.connection = connection;
        this.kube = kube;
    }

    public String getImageDescriptor(String kind, String box) {
        K8sCustomResource resource = kube.loadCustomResource(ResourceType.forKind(kind), box);
        if (resource != null) {
            Object spec = resource.getSpec();
            String imageName = SpecUtils.getImageName(spec);
            String version = SpecUtils.getImageVersion(spec);
            return getDescriptor(imageName, version);
        }
        String errorMessage = String.format("Couldn't find resource: '%s' on cluster", ResourcePath.annotationFor(kube.getNamespaceName(), kind, box));
        throw new ResourceNotFoundException(errorMessage);
    }

    private String getDescriptor(String imageName, String version) {
        Map<String, String> imageLabels = getImageLabels(imageName, version);
        if (imageLabels == null) {
            return null;
        }
        return imageLabels.get(DESCRIPTOR_ALIAS);
    }

    private Map<String, String> getImageLabels(String imageName, String version) {
        ImageManifestV2 manifest = connection.getImageManifest(imageName, version);
        if (manifest == null) {
            throw new ManifestNotFoundException(String.format("Couldn't execute request or couldn't find manifest for image: '%s:%s'", imageName, version));
        }
        String digest = manifest.getConfig().getDigest();
        Blob blob = connection.getBlob(imageName, digest);
        if (blob == null) {
            throw new BlobNotFoundException(String.format("Couldn't execute request or couldn't find blob for digest: '%s'", digest));
        }
        return blob.getConfig().getLabels();
    }
}
