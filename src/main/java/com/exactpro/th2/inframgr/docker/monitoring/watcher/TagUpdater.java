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

import static com.exactpro.th2.inframgr.docker.util.VersionNumberUtils.chooseLatestVersion;
import static com.exactpro.th2.inframgr.docker.util.VersionNumberUtils.filterTags;

public class TagUpdater {

    private static final int PAGE_SIZE = 200;

    private TagUpdater() {
    }

    static void getLatestTags(DynamicResource resource, List<SchemaJob.UpdatedResource> updatedResources, RegistryConnection connection) {
        String latestTag = chooseLatestVersion(getNewerTags(resource, connection));
        if (latestTag != null) {
            updatedResources.add(new SchemaJob.UpdatedResource(resource.getName(), resource.getVersionRange() + latestTag));
        }
    }

    private static List<String> getNewerTags(DynamicResource resource, RegistryConnection connection) {
        String currentVersion = resource.getCurrentVersion();
        String image = resource.getImage();
        List<String> allHigherTags = new ArrayList<>();
        List<String> tags;

        //get tags in small amounts starting from current image-version
        do {
            tags = connection.getTags(image, PAGE_SIZE, currentVersion);
            if (tags == null || tags.size() < 1) {
                break;
            }
            allHigherTags.addAll(filterTags(tags, resource.getVersionRange()));
            currentVersion = tags.get(tags.size() - 1);
        } while (!(tags.size() < PAGE_SIZE));

        return allHigherTags;
    }
}
