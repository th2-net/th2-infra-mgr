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
        for (RequestEntry entry : operations) {
            repositoryResources.add(entry.getPayload().toRepositoryResource());
        }

        int initialSize = repositoryResources.size();
        detectUrlPathsConflicts(repositoryResources, branch);
        if (initialSize > repositoryResources.size())
            operations.removeIf(entry -> !repositoryResources.contains(entry.getPayload().toRepositoryResource()));
    }

    private static Map<RepositoryResource, Set<String>> getRepositoryUrlPaths(Set<RepositoryResource> resources,
                                                                              String branch) {
        Map<RepositoryResource, Set<String>> map = new HashMap<>();
        for (RepositoryResource r : resources) {

            var spec = (Map<String, Object>) r.getSpec();
            if (spec == null || !spec.containsKey("extended-settings")) continue;

            var settings = (Map<String, Object>) spec.get("extended-settings");
            if (settings == null || !settings.containsKey("service")) continue;

            var service = (Map<String, Object>) settings.get("service");
            if (service == null || !service.containsKey("ingress")) continue;

            var ingress = (Map<String, Object>) service.get("ingress");
            if (ingress == null || !ingress.containsKey("urlPaths")) continue;

            List<String> urls = (List<String>) ingress.get("urlPaths");
            if (urls == null || urls.isEmpty()) continue;

            Set<String> urlPaths = new HashSet<>();
            List<String> duplicated = new ArrayList<>();
            for (String url : urls) {
                if (!(urlPaths.add(url) || duplicated.contains(url))) {
                    duplicated.add(url);
                }
            }

            if (!duplicated.isEmpty()) {
                logger.warn("Duplication of url paths {} in resource \"{}\" in schema \"{}\"",
                    duplicated, r.getMetadata().getName(), branch);

                ingress.put("urlPaths", new ArrayList<>(urlPaths));
            }

            map.put(r, urlPaths);
        }

        return map;
    }
}
