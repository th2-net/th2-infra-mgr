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
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import com.exactpro.th2.infrarepo.RepositoryResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UrlPathConflicts {

    private static final Logger logger = LoggerFactory.getLogger(UrlPathConflicts.class);

    public static Set<RepositoryResource> detectUrlPathsConflicts(Set<RepositoryResource> repositoryResources, String branch) {

        Map<String, Set<String>> map = getRepositoryUrlPaths(repositoryResources, branch);
        if (map.isEmpty()) return repositoryResources;

        Set<String> conflictedResourceLabels = new HashSet<>();
        List<Map.Entry<String, Set<String>>> entries = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry1 : map.entrySet()) {
            entries.add(entry1);
            if (entries.size() == map.size()) break;

            for (Map.Entry<String, Set<String>> entry2 : map.entrySet()) {
                if (entries.contains(entry2)) continue;

                List<String> duplicated = new ArrayList<>();
                Set<String> checker = new HashSet<>(entry1.getValue());
                for (String url : entry2.getValue())
                    if (!checker.add(url)) duplicated.add(url);

                if (!duplicated.isEmpty()) {
                    logger.error("Conflicts of url paths {} between resources \"{}\" and \"{}\"",
                        duplicated, entry1.getKey(), entry2.getKey());
                    conflictedResourceLabels.add(entry1.getKey());
                    conflictedResourceLabels.add(entry2.getKey());
                }
            }
        }
        if (conflictedResourceLabels.isEmpty()) return repositoryResources;

        conflictedResourceLabels.forEach(map::remove);
        Set<RepositoryResource> validResources = new HashSet<>();
        Set<String> keys = map.keySet();
        for (RepositoryResource resource : repositoryResources)
            if (keys.contains(ResourcePath.annotationFor(branch, resource.getKind(), resource.getMetadata().getName())))
                validResources.add(resource);

        return validResources;
    }

    public static List<RequestEntry> detectUrlPathsConflicts(List<RequestEntry> operations, String branch) {

        Set<RepositoryResource> repositoryResources = new HashSet<>();
        for (RequestEntry entry : operations)
            repositoryResources.add(entry.getPayload().toRepositoryResource());

        int initialSize = repositoryResources.size();
        repositoryResources = detectUrlPathsConflicts(repositoryResources, branch);
        if (initialSize == repositoryResources.size()) return operations;

        Set<String> names = new HashSet<>();
        for (RepositoryResource resource : repositoryResources)
            names.add(resource.getMetadata().getName());

        List<RequestEntry> validOperations = new ArrayList<>();
        for (RequestEntry entry : operations)
            if (names.contains(entry.getPayload().toRepositoryResource().getMetadata().getName()))
                validOperations.add(entry);

        return validOperations;
    }

    private static Map<String, Set<String>> getRepositoryUrlPaths(Set<RepositoryResource> resources,
                                                                  String branch) {
        Map<String, Set<String>> map = new HashMap<>();
        for (RepositoryResource resource : resources) {

            var spec = (Map<String, Object>) resource.getSpec();
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
            for (String url : urls)
                if (!(urlPaths.add(url) || duplicated.contains(url)))
                    duplicated.add(url);

            String resourceLabel = ResourcePath.annotationFor(
                branch, resource.getKind(), resource.getMetadata().getName());

            if (!duplicated.isEmpty()) {
                logger.warn("Duplication of url paths {} in resource \"{}\"",
                    duplicated, resourceLabel);
                ingress.put("urlPaths", new ArrayList<>(urlPaths));
            }
            map.put(resourceLabel, urlPaths);
        }

        return map;
    }
}
