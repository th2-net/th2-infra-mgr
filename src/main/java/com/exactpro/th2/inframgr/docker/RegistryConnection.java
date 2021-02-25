package com.exactpro.th2.inframgr.docker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RegistryConnection {
    private static final String URL_PREFIX = "https://";
    private static final String API_SUFFIX = "/v2";
    private static final char SLASH_CHAR = '/';

    private Map<String, SecretMapper.AuthenticationDetails> secrets;

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

    public List<String> getTags(String imageName, int n) {
        String tagsUrl = String.format("/tags/list?n=%s", n);
        return requestTags(
                toApiUrl(imageName, tagsUrl),
                getAuthenticationDetails(imageName)
        );
    }

    public List<String> getTags(String imageName, int n, String last) {
        String tagsUrl = String.format("/tags/list?n=%s&last=%s", n, last);
        return requestTags(toApiUrl(imageName, tagsUrl),
                getAuthenticationDetails(imageName)
        );
    }

    private List<String> requestTags(String url, SecretMapper.AuthenticationDetails authenticationDetails) {
        RestTemplate restTemplate = new RestTemplateBuilder().basicAuthentication(
                authenticationDetails.getUser(),
                authenticationDetails.getPassword()
        ).build();
        TagResponseBody tagResponseBody = restTemplate.getForObject(url, TagResponseBody.class);
        return tagResponseBody == null ? Collections.EMPTY_LIST : tagResponseBody.tags;
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
