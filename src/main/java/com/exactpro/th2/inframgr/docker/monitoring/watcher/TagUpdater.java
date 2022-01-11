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

package com.exactpro.th2.inframgr.docker.monitoring.watcher;

import com.exactpro.th2.inframgr.docker.monitoring.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;

import java.util.ArrayList;
import java.util.List;

import static com.exactpro.th2.inframgr.docker.util.VersionNumberUtils.chooseLatest;
import static com.exactpro.th2.inframgr.docker.util.VersionNumberUtils.filterTags;
import static com.exactpro.th2.inframgr.statuswatcher.ResourcePath.annotationFor;

public class TagUpdater {

    private static final int PAGE_SIZE = 200;

    static void checkLatestTags(DynamicResource resource,
                                List<SchemaJob.UpdatedResource> updatedResources,
                                RegistryConnection connection) {
        String image = resource.getImage();
        String resourceLabel = annotationFor(resource.getSchema(), resource.getKind(), resource.getName());
        //get tags in small pages
        List<String> tags = connection.getTags(resourceLabel, image, PAGE_SIZE);
        List<String> filteredTags = new ArrayList<>(filterTags(tags, resource.getVersionRange()));
        while (tags.size() >= PAGE_SIZE) {
            String currentVersion = tags.get(tags.size() - 1);
            tags = connection.getTags(resourceLabel, image, PAGE_SIZE, currentVersion);
            filteredTags.addAll(filterTags(tags, resource.getVersionRange()));
        }
        String latestTagSuffix = chooseLatest(filteredTags);
        if (latestTagSuffix != null) {
            String latestVersion = resource.getVersionRange() + latestTagSuffix;
            updatedResources.add(new SchemaJob.UpdatedResource(resource.getName(), latestVersion));
        }
    }
}
