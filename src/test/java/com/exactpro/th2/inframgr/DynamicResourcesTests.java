/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.inframgr.docker.monitoring.DynamicResource;
import com.exactpro.th2.inframgr.docker.monitoring.DynamicResourceProcessor;
import com.exactpro.th2.inframgr.docker.monitoring.DynamicResourcesCache;
import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import com.exactpro.th2.infrarepo.RepositoryResource;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.exactpro.th2.infrarepo.ResourceType.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.exactpro.th2.inframgr.docker.util.SpecUtils.*;

class DynamicResourcesTests {
    private static final DynamicResourcesCache cache = DynamicResourcesCache.INSTANCE;

    private static final int RULES = 2; //number of cases wherein resource gets deleted from cache

    private static final List<RepositoryResource> validRepoResList = new ArrayList<>();

    private static final List<RepositoryResource> invalidRepoResList = new ArrayList<>();

    private static final String SCHEMA_1 = "infra-dev";

    private static final String SCHEMA_2 = "infra-qa";

    @BeforeAll
    static void init() {
        fillValidRepoResList();
        fillInvalidRepoResList();
    }

    private static void fillValidRepoResList() {
        RepositoryResource res1 = new RepositoryResource(Th2CoreBox);
        res1.setMetadata(new RepositoryResource.Metadata("res1"));
        Map<String, String> spec1 = Map.of(
                VERSION_RANGE_ALIAS, "1.+",
                IMAGE_NAME_ALIAS, "some-image",
                IMAGE_VERSION_ALIAS, "1.2.3"
        );
        res1.setSpec(spec1);
        validRepoResList.add(res1);
        RepositoryResource res2 = new RepositoryResource(Th2Estore);
        res2.setMetadata(new RepositoryResource.Metadata("res2"));
        Map<String, String> spec2 = Map.of(
                VERSION_RANGE_ALIAS, "3.+",
                IMAGE_NAME_ALIAS, "another-image",
                IMAGE_VERSION_ALIAS, "3.10"
        );
        res2.setSpec(spec2);
        validRepoResList.add(res2);
    }

    private static void fillInvalidRepoResList() {
        for (int i = 0; i < validRepoResList.size(); i++) {
            RepositoryResource valid = validRepoResList.get(i);
            Object validSpec = valid.getSpec();
            RepositoryResource invalid = new RepositoryResource();
            invalid.setMetadata(valid.getMetadata());
            invalid.setKind(valid.getKind());
            if (i % RULES == 0) {
                invalid.setSpec(null);
            }
            if (i % RULES == 1) {
                //no version range
                invalid.setSpec(Map.of(
                        IMAGE_NAME_ALIAS, SpecUtils.getImageName(validSpec),
                        IMAGE_VERSION_ALIAS, SpecUtils.getImageVersion(validSpec)
                ));
            }
            //can add more if rules increase

            invalidRepoResList.add(invalid);
        }
    }

    @BeforeEach
    void invalidateCache() {
        cache.clear();
    }

    @Test
    void testDynamicResource() {
        DynamicResource res = convertToDynamicRes(validRepoResList.get(1), SCHEMA_2);
        DynamicResource equalRes = convertToDynamicRes(validRepoResList.get(1), SCHEMA_2);
        DynamicResource notEqualRes = convertToDynamicRes(validRepoResList.get(1), SCHEMA_1);
        assertEquals(res, equalRes);
        assertNotEquals(res, notEqualRes);
        assertEquals(SCHEMA_2 + "." + res.getName(), res.getAnnotation());
    }

    @Test
    void testSchemas() {
        RepositoryResource res = validRepoResList.get(0);
        DynamicResourceProcessor.checkResource(res, SCHEMA_1);
        assertEquals(1, cache.getSchemas().size());
        assertEquals(Set.of(SCHEMA_1), (cache.getSchemas()));
        DynamicResourceProcessor.checkResource(res, SCHEMA_2);
        assertEquals(Set.of(SCHEMA_1, SCHEMA_2), cache.getSchemas());

        DynamicResourceProcessor.deleteSchema(SCHEMA_1);
        assertEquals(1, cache.getSchemas().size());
        DynamicResourceProcessor.deleteSchema(SCHEMA_2);
        assertEquals(Collections.emptySet(), cache.getSchemas());
    }

    @Test
    void testGetDynamicResourcesCopy() {
        RepositoryResource res1 = validRepoResList.get(0);
        RepositoryResource res2 = validRepoResList.get(1);
        DynamicResourceProcessor.checkResource(res1, SCHEMA_1);
        DynamicResourceProcessor.checkResource(res2, SCHEMA_1);
        DynamicResource dynamicRes1 = convertToDynamicRes(res1, SCHEMA_1);
        DynamicResource dynamicRes2 = convertToDynamicRes(res2, SCHEMA_1);
        assertEquals(Set.of(dynamicRes1, dynamicRes2),
                new HashSet<>(cache.getDynamicResourcesCopy(SCHEMA_1)));
    }

    @Test
    void testDynamicResourceFullDeletion() {
        RepositoryResource res1 = validRepoResList.get(0);
        DynamicResourceProcessor.checkResource(res1, SCHEMA_1);
        DynamicResourceProcessor.checkResource(res1, SCHEMA_1, true);
        assertTrue(cache.getDynamicResourcesCopy(SCHEMA_1).isEmpty());
        validRepoResList.forEach(res -> DynamicResourceProcessor.checkResource(res, SCHEMA_1));
        invalidRepoResList.forEach(res ->  DynamicResourceProcessor.checkResource(res, SCHEMA_1));

        assertTrue(cache.getDynamicResourcesCopy(SCHEMA_1).isEmpty());
    }

    @Test
    void testDynamicResourcePartlyDeletion() {
        invalidRepoResList.remove(invalidRepoResList.size() - 1);
        validRepoResList.forEach(res -> DynamicResourceProcessor.checkResource(res, SCHEMA_1));
        invalidRepoResList.forEach(res ->  DynamicResourceProcessor.checkResource(res, SCHEMA_1));
        DynamicResource lastValid = convertToDynamicRes(
                validRepoResList.get(validRepoResList.size() - 1),
                SCHEMA_1);
        assertEquals(1, cache.getDynamicResourcesCopy(SCHEMA_1).size());
        assertTrue(cache.getDynamicResourcesCopy(SCHEMA_1).contains(lastValid));
    }

    private static DynamicResource convertToDynamicRes(RepositoryResource res, String schemaName) {
        Object spec = res.getSpec();
        String choppedVersionRange = StringUtils.chop(SpecUtils.getImageVersionRange(spec));
        return new DynamicResource(res.getMetadata().getName(), res.getKind(),
                SpecUtils.getImageName(spec), SpecUtils.getImageVersion(spec),
                choppedVersionRange, schemaName);
    }

}
