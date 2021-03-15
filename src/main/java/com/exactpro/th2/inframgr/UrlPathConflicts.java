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

        // get map of resource label and url paths pairs
        Map<String, Set<String>> repositoryUrlPaths = getRepositoryUrlPaths(repositoryResources, branch);
        // if no resource or just one resource contains url paths then there can't be conflicts
        if (repositoryUrlPaths.size() < 2)
            return repositoryResources;

        Set<String> conflictedResourceLabels = new HashSet<>();
        Set<String> entries = new HashSet<>();

        // detect conflicts, if any
        for (var entry1 : repositoryUrlPaths.entrySet()) {
            entries.add(entry1.getKey());

            for (var entry2 : repositoryUrlPaths.entrySet()) {
                // avoid comparing resource with itself and comparing the same resources twice
                if (entries.contains(entry2.getKey()))
                    continue;

                List<String> duplicated = new ArrayList<>();
                Set<String> checker = new HashSet<>(entry1.getValue());
                // use the set 'checker' to detect url duplications between resources
                for (String url : entry2.getValue())
                    if (checker.contains(url))
                        duplicated.add(url);

                if (!duplicated.isEmpty()) {
                    logger.error("Conflicts of url paths {} between resources \"{}\" and \"{}\"",
                        duplicated, entry1.getKey(), entry2.getKey());
                    conflictedResourceLabels.add(entry1.getKey());
                    conflictedResourceLabels.add(entry2.getKey());
                }
            }
        }
        if (conflictedResourceLabels.isEmpty())
            return repositoryResources;

        Set<RepositoryResource> validResources = new HashSet<>();
        for (RepositoryResource resource : repositoryResources)
            // add resources with no url path conflicts to valid resources
            if (!conflictedResourceLabels.contains(
                ResourcePath.annotationFor(branch, resource.getKind(), resource.getMetadata().getName())))
                validResources.add(resource);

        return validResources;
    }

    public static List<RequestEntry> detectUrlPathsConflicts(List<RequestEntry> operations, String branch) {

        Set<RepositoryResource> repositoryResources = new HashSet<>();
        // extract repository resources from the operations
        for (RequestEntry entry : operations)
            repositoryResources.add(entry.getPayload().toRepositoryResource());

        int initialSize = repositoryResources.size();
        repositoryResources = detectUrlPathsConflicts(repositoryResources, branch);
        // in case of conflicts the size of 'repositoryResources' would be reduced
        if (initialSize == repositoryResources.size())
            return operations;

        Set<String> namesOfValidResources = new HashSet<>();
        for (RepositoryResource resource : repositoryResources)
            namesOfValidResources.add(resource.getMetadata().getName());

        List<RequestEntry> validOperations = new ArrayList<>();
        for (RequestEntry entry : operations)
            if (namesOfValidResources.contains(entry.getPayload().toRepositoryResource().getMetadata().getName()))
                validOperations.add(entry);

        return validOperations;
    }

    private static Map<String, Set<String>> getRepositoryUrlPaths(Set<RepositoryResource> resources,
                                                                  String branch) {
        Map<String, Set<String>> map = new HashMap<>();
        for (RepositoryResource resource : resources) {

            String resourceLabel = ResourcePath.annotationFor(branch, resource.getKind(), resource.getMetadata().getName());
            try {
                var spec = (Map<String, Object>) resource.getSpec();
                if (spec == null)
                    continue;

                var settings = (Map<String, Object>) spec.get("extended-settings");
                if (settings == null)
                    continue;

                var service = (Map<String, Object>) settings.get("service");
                if (service == null)
                    continue;

                var ingress = (Map<String, Object>) service.get("ingress");
                if (ingress == null)
                    continue;

                List<String> urls = (List<String>) ingress.get("urlPaths");
                if (urls == null || urls.isEmpty())
                    continue;

                Set<String> urlPaths = new HashSet<>();
                Set<String> duplicated = new HashSet<>();
                // use the set 'urlPaths' to detect url duplication in a resource
                for (String url : urls)
                    if (!urlPaths.add(url))
                        duplicated.add(url);

                if (!duplicated.isEmpty()) {
                    logger.warn("Resource \"{}\" contains duplicate urlPath entries {}", resourceLabel, duplicated);
                    // put fixed urlPaths property in the resource
                    ingress.put("urlPaths", new ArrayList<>(urlPaths));
                }
                // collect resources that contain url paths
                map.put(resourceLabel, urlPaths);

            } catch (ClassCastException e) {
                logger.error("Exception extracting urlPaths property from \"{}\"", resourceLabel, e);
            }
        }

        return map;
    }
}
