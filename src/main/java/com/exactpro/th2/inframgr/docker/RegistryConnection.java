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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RegistryConnection {
    private static final Logger logger = LoggerFactory.getLogger(RegistryConnection.class);

    private static final String URL_PREFIX = "https://";
    private static final String API_SUFFIX = "/v2";
    private static final char SLASH_CHAR = '/';

    private final Map<String, SecretMapper.AuthenticationDetails> secrets;

    public RegistryConnection(Map<String, SecretMapper.AuthenticationDetails> secrets) {
        this.secrets = secrets;
    }

    public List<String> getTags(String imageName) {
        String tagsUrl = "/tags/list";
        return requestTags(
                toApiUrl(imageName, tagsUrl),
                getAuthenticationDetails(imageName)
        );
    }

    public List<String> getTags(String imageName, int count) {
        String tagsUrl = String.format("/tags/list?n=%d", count);
        return requestTags(
                toApiUrl(imageName, tagsUrl),
                getAuthenticationDetails(imageName)
        );
    }

    public List<String> getTags(String imageName, int count, String last) {
        String tagsUrl = String.format("/tags/list?n=%d&last=%s", count, last);
        return requestTags(toApiUrl(imageName, tagsUrl),
                getAuthenticationDetails(imageName)
        );
    }

    private List<String> requestTags(String url, SecretMapper.AuthenticationDetails authenticationDetails) {
        TagResponseBody tagResponseBody = null;
        RestTemplate restTemplate = buildRest(authenticationDetails);
        try {
            tagResponseBody = restTemplate.getForObject(url, TagResponseBody.class);
        } catch (Exception e) {
            logger.info("Exception executing request: {}", url, e);
        }
        return tagResponseBody == null ? Collections.EMPTY_LIST : tagResponseBody.tags;
    }

    private RestTemplate buildRest(SecretMapper.AuthenticationDetails authenticationDetails) {
        RestTemplateBuilder builder = new RestTemplateBuilder();
        if (authenticationDetails == null) {
           return builder.basicAuthentication("USER", "PASSWORD").build();
        } else {
           return builder.basicAuthentication(
                    authenticationDetails.getUser(),
                    authenticationDetails.getPassword()
            ).build();
        }
    }

    private SecretMapper.AuthenticationDetails getAuthenticationDetails(String imageName) {
        String registry = imageName.substring(0, imageName.indexOf(SLASH_CHAR));
        return secrets.get(registry);
    }

    private String toApiUrl(String imageName, String request) {
        int position = URL_PREFIX.length() + imageName.indexOf(SLASH_CHAR);
        return new StringBuilder(URL_PREFIX)
                .append(imageName)
                .insert(position, API_SUFFIX)
                .append(request)
                .toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TagResponseBody {
        private String name;
        private List<String> tags;

        public String getName() {
            return name;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }
}
