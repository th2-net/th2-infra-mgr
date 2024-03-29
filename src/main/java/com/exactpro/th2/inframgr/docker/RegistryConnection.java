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

package com.exactpro.th2.inframgr.docker;

import com.exactpro.th2.inframgr.docker.descriptor.errors.InvalidImageNameFormatException;
import com.exactpro.th2.inframgr.docker.descriptor.errors.RegistryRequestException;
import com.exactpro.th2.inframgr.docker.model.schemav2.Blob;
import com.exactpro.th2.inframgr.docker.model.schemav2.ImageManifestV2;
import com.exactpro.th2.inframgr.docker.model.tag.TagResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RegistryConnection {

    private static final Logger logger = LoggerFactory.getLogger(RegistryConnection.class);

    private static final String URL_PREFIX = "https://";

    private static final String API_SUFFIX = "/v2";

    private static final char SLASH_CHAR = '/';

    private final Map<String, RegistryCredentialLookup.RegistryCredentials> secrets;

    public RegistryConnection(Map<String, RegistryCredentialLookup.RegistryCredentials> secrets) {
        this.secrets = secrets;
    }

    public List<String> getTags(String resName, String imageName) {
        String tagsUrl = "/tags/list";
        return requestTags(
                toApiUrl(imageName, tagsUrl),
                getAuthenticationDetails(resName, imageName)
        );
    }

    public List<String> getTags(String resName, String imageName, int count) {
        String tagsUrl = String.format("/tags/list?n=%d", count);
        return requestTags(
                toApiUrl(imageName, tagsUrl),
                getAuthenticationDetails(resName, imageName)
        );
    }

    public List<String> getTags(String resName, String imageName, int count, String last) {
        String tagsUrl = String.format("/tags/list?n=%d&last=%s", count, last);
        return requestTags(toApiUrl(imageName, tagsUrl),
                getAuthenticationDetails(resName, imageName)
        );
    }

    public ImageManifestV2 getImageManifest(String resName, String imageName, String version) {
        String manifestsUrl = String.format("/manifests/%s", version);
        return requestImageManifest(toApiUrl(imageName, manifestsUrl),
                getAuthenticationDetails(resName, imageName));
    }

    public Blob getBlob(String resName, String imageName, String digest) {
        String blobsUrl = String.format("/blobs/%s", digest);
        return requestBlobs(toApiUrl(imageName, blobsUrl),
                getAuthenticationDetails(resName, imageName));
    }

    private List<String> requestTags(String url, RegistryCredentialLookup.RegistryCredentials authenticationDetails) {
        TagResponseBody tagResponseBody = null;
        RestTemplate restTemplate = buildRest(authenticationDetails);
        HttpEntity<String> entity = null;
        if (authenticationDetails == null) {
            entity = new HttpEntity<>(createHeaders());
        }
        try {
            tagResponseBody = restTemplate.exchange(url, HttpMethod.GET, entity, TagResponseBody.class).getBody();
        } catch (Exception e) {
            logger.error("Exception executing request: {}", url, e);
        }
        return tagResponseBody == null ? Collections.EMPTY_LIST : tagResponseBody.getTags();
    }

    private ImageManifestV2 requestImageManifest(String url,
                                                 RegistryCredentialLookup.RegistryCredentials authenticationDetails) {
        RestTemplate restTemplate = buildRest(authenticationDetails);
        HttpHeaders headers = new HttpHeaders();
        if (authenticationDetails == null) {
            headers = createHeaders();
        }
        headers.add("Accept", "application/vnd.docker.distribution.manifest.v2+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            return restTemplate.exchange(url, HttpMethod.GET, entity, ImageManifestV2.class).getBody();
        } catch (Exception e) {
            logger.error("Exception executing request: {}", url, e);
            throw new RegistryRequestException(e);
        }
    }

    private Blob requestBlobs(String url, RegistryCredentialLookup.RegistryCredentials authenticationDetails) {
        RestTemplate restTemplate = buildRest(authenticationDetails);
        //dummy header for github
        HttpEntity<String> entity = null;
        if (authenticationDetails == null) {
            entity = new HttpEntity<>(createHeaders());
        }

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();

        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.ALL));
        restTemplate.setMessageConverters(Arrays.asList(converter, new FormHttpMessageConverter()));
        try {
            return restTemplate.exchange(url, HttpMethod.GET, entity, Blob.class).getBody();
        } catch (Exception e) {
            logger.error("Exception executing request: {}", url, e);
            throw new RegistryRequestException(e);
        }
    }

    private RestTemplate buildRest(RegistryCredentialLookup.RegistryCredentials authenticationDetails) {
        RestTemplateBuilder builder = new RestTemplateBuilder();
        if (authenticationDetails != null) {
            return builder.basicAuthentication(
                    authenticationDetails.getUser(),
                    authenticationDetails.getPassword()
            ).build();
        }
        return builder.build();
    }

    private RegistryCredentialLookup.RegistryCredentials getAuthenticationDetails(String resName, String imageName) {
        try {
            String registry = imageName.substring(0, imageName.indexOf(SLASH_CHAR));
            return secrets.get(registry);
        } catch (Exception e) {
            String message = String.format("Invalid image format \"%s\" in \"%s\"", imageName, resName);
            logger.error(message);
            throw new InvalidImageNameFormatException(message, e);
        }
    }

    private String toApiUrl(String imageName, String request) {
        int position = URL_PREFIX.length() + imageName.indexOf(SLASH_CHAR);
        return new StringBuilder(URL_PREFIX)
                .append(imageName)
                .insert(position, API_SUFFIX)
                .append(request)
                .toString();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer null");
        return headers;
    }
}
