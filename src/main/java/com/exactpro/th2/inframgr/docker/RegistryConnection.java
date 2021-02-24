package com.exactpro.th2.inframgr.docker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.util.List;

public class RegistryConnection {

    //TODO get from config
    private final String registry = "https://nexus.exactpro.com:16000";
    private final String user = "th2-schema-docker";
    private final String password = "feiL5tie";

    private NexusAuth nexusAuth = new NexusAuth(registry, user, password);

    RestTemplateBuilder builder = new RestTemplateBuilder();
    RestTemplate restTemplate = builder.basicAuthentication(nexusAuth.user, nexusAuth.password).build();

    public TagResponseBody getTags() {
        String url = "/v2/th2-infra-operator/tags/list";
        return getTags(url);
    }

    public TagResponseBody getTags(int n) {
        String url = String.format("/v2/th2-infra-operator/tags/list?n=%s", n);
        return getTags(url);
    }

    public TagResponseBody getTags(int n, String last) {
        String url = String.format("/v2/th2-infra-operator/tags/list?n=%s&last=%s", n, last);
        return getTags(url);
    }

    private TagResponseBody getTags(String url) {
        return restTemplate.getForObject(nexusAuth.registry + url, TagResponseBody.class);
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

    private static final class NexusAuth {
        private String registry;
        private String user;
        private String password;

        public NexusAuth(String registry, String user, String password) {
            this.registry = registry;
            this.user = user;
            this.password = password;
        }
    }
}
