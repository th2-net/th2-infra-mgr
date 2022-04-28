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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.exactpro.th2.inframgr.docker.util.VersionNumberUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class VersionNumberUtilsTests {
    private static List<String> tags;

    @BeforeAll
    static void initTags() {
        tags = Arrays.asList(
                "2.1.0",
                "2.1.1",
                "2.1.2",
                "2.2.0",
                "2.2.1",
                "2.2.2",
                "3.0.0",
                "3.0.1-th21450-598864005",
                "3.0.1-th21450-599407903",
                "3.1.0-th21450-610367611",
                "2.3.0",
                "3.1.0",
                "3.1.1",
                "2.4.0",
                "3.2.0-th2-1769-729682817",
                "3.2.0",
                "2.4.1-TH2-1766-v2-732761325",
                "3.2.1",
                "3.3.0-th2-121-v3-740351306",
                "3.2.2-TH2-1766-v3-744530724",
                "3.2.2-TH2-1766-v3-746964701",
                "3.9.0-th2-2325-1213490116",
                "3.9.0",
                "3.10.0-th2-2095-1229438839",
                "3.10.0-th2-2095-1230647623",
                "3.10.0-th2-2095-1231377071",
                "3.10.0",
                "3.10.1-bump-sf-core-1265013193",
                "3.10.1",
                "3.10.1-th2-2273-1270201983",
                "3.30.1",
                "3.100.1"
        );

        Collections.sort(tags);
    }

    @Test
    void testValidate() {
        String versionRange = "2.1.1-th2-";
        String tag = "2.1.1-th2-1.1";
        assertTrue(validate(tag, versionRange));
        tag = "2.1.1-1.1";
        assertFalse(validate(tag, versionRange));
        versionRange = "2.";
        assertTrue(validate(tag, versionRange));
    }

    @Test
    void testFilterTags1() {
        String versionRange = "3.10.";
        List<String> expectedList = Arrays.asList(
                "0",
                "0-th2-2095-1229438839",
                "0-th2-2095-1230647623",
                "0-th2-2095-1231377071",
                "1",
                "1-th2-2273-1270201983",
                "1-bump-sf-core-1265013193"
        );
        Collections.sort(expectedList);
        List<String> filteredTags = filterTags(tags, versionRange);
        Collections.sort(filteredTags);
        assertEquals(expectedList, filteredTags);
    }

    @Test
    void testFilterTags2() {
        String versionRange = "3.100.";
        List<String> expectedList = List.of("1");
        assertEquals(expectedList, filterTags(tags, versionRange));
        versionRange = "5.0.";
        assertEquals(Collections.emptyList(), filterTags(tags, versionRange));
    }

    @Test
    void testChooseLatest1() {
        String versionRange = "3.";
        List<String> filteredTags = filterTags(tags, versionRange);
        String latest = chooseLatest(filteredTags);
        assertEquals("3.100.1", versionRange + latest);
    }

    @Test
    void testChooseLatest2() {
        String versionRange = "";
        List<String> filteredTags = filterTags(tags, versionRange);
        String latest = chooseLatest(filteredTags);
        assertEquals("3.100.1", versionRange + latest);
    }

    @Test
    void testChooseLatestOnEmptyList() {
        assertNull(chooseLatest(Collections.emptyList()));
    }
}
