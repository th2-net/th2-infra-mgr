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

import com.exactpro.th2.inframgr.models.RequestEntry;
import com.exactpro.th2.infrarepo.RepositoryResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UrlPathConflicts {

    private static final Logger logger = LoggerFactory.getLogger(UrlPathConflicts.class);

    public static void detectUrlPathsConflicts(Set<RepositoryResource> repositoryResources, String branch) {

        Map<RepositoryResource, Set<String>> map = getRepositoryUrlPaths(repositoryResources, branch);
        if (map.isEmpty()) return;

        List<Set<String>> urlPaths = new ArrayList<>(map.values());
        Set<Set<String>> conflictedUrlPaths = new HashSet<>();

        for (int i = 0; i < urlPaths.size() - 1; i++)
            for (int j = i + 1; j < urlPaths.size(); j++) {
                Set<String> value1 = urlPaths.get(i);
                Set<String> value2 = urlPaths.get(j);
                Set<String> set = new HashSet<>(value1);
                set.addAll(value2);

                if (value1.size() + value2.size() > set.size()) {
                    conflictedUrlPaths.add(value1);
                    conflictedUrlPaths.add(value2);
                }
            }

        Set<RepositoryResource> conflictedResources = new HashSet<>();
        if (!conflictedUrlPaths.isEmpty()) {
            for (Map.Entry<RepositoryResource, Set<String>> entry : map.entrySet())
                if (conflictedUrlPaths.contains(entry.getValue()))
                    conflictedResources.add(entry.getKey());

            repositoryResources.removeAll(conflictedResources);
            List<String> conflictedResourceNames = new ArrayList<>();
            conflictedResources.forEach(resource -> conflictedResourceNames.add(resource.getMetadata().getName()));
            logger.error("Url path conflicts between resources {} in schema \"{}\"",
                conflictedResourceNames, branch);
        }
    }

    public static void detectUrlPathsConflicts(List<RequestEntry> operations, String branch) {

        Set<RepositoryResource> repositoryResources = new HashSet<>();
        for (RequestEntry entry : operations){
            repositoryResources.add(entry.getPayload().toRepositoryResource());
        }
        Map<RepositoryResource, Set<String>> map = getRepositoryUrlPaths(repositoryResources, branch);
        if (map.isEmpty()) return;

        List<Set<String>> urlPaths = new ArrayList<>(map.values());
        Set<Set<String>> conflictedUrlPaths = new HashSet<>();

        for (int i = 0; i < urlPaths.size() - 1; i++)
            for (int j = i + 1; j < urlPaths.size(); j++) {
                Set<String> value1 = urlPaths.get(i);
                Set<String> value2 = urlPaths.get(j);
                Set<String> set = new HashSet<>(value1);
                set.addAll(value2);

                if (value1.size() + value2.size() > set.size()) {
                    conflictedUrlPaths.add(value1);
                    conflictedUrlPaths.add(value2);
                }
            }

        Set<RepositoryResource> conflictedResources = new HashSet<>();
        if (!conflictedUrlPaths.isEmpty()) {
            for (Map.Entry<RepositoryResource, Set<String>> entry : map.entrySet())
                if (conflictedUrlPaths.contains(entry.getValue()))
                    conflictedResources.add(entry.getKey());

            operations.removeIf(entry -> conflictedResources.contains(entry.getPayload().toRepositoryResource()));
            List<String> conflictedResourceNames = new ArrayList<>();
            conflictedResources.forEach(resource -> conflictedResourceNames.add(resource.getMetadata().getName()));
            logger.error("Url path conflicts between resources {} in schema \"{}\"",
                conflictedResourceNames, branch);
        }
    }

    private static Map<RepositoryResource, Set<String>> getRepositoryUrlPaths(Set<RepositoryResource> resources,
                                                                              String branch) {
        Map<RepositoryResource, Set<String>> map = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();

        for (RepositoryResource r : resources) {
            Set<String> urlPaths;
            try {
                var spec = mapper.convertValue(r.getSpec(), Map.class);
                var settings = mapper.convertValue(spec.get("extended-settings"), Map.class);
                var service = mapper.convertValue(settings.get("service"), Map.class);
                var ingress = mapper.convertValue(service.get("ingress"), Map.class);
                List<String> list = mapper.convertValue(ingress.get("urlPaths"), List.class);

                urlPaths = new HashSet<>(list);
                if (urlPaths.size() < list.size()) {
                    logger.warn("Url path duplication in resource \"{}\" in schema \"{}\"",
                        r.getMetadata().getName(), branch);
                    ingress.put("urlPaths", new ArrayList<>(urlPaths));
                    service.put("ingress", ingress);
                    settings.put("service", service);
                    spec.put("extended-settings", settings);
                    r.setSpec(spec);
                }
            } catch (Exception e) {
                continue;
            }
            if (!urlPaths.isEmpty())
                map.put(r, urlPaths);
        }

        return map;
    }
}
