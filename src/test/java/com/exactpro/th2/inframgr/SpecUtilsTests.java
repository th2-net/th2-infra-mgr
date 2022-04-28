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

import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import static com.exactpro.th2.inframgr.docker.util.SpecUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SpecUtilsTests {
    private static final Map<String, String> spec = new HashMap<>();

    private static final String IMAGE_NAME = "some-image";

    private static final String IMAGE_VERSION = "3.0";

    private static final String VERSION_RANGE = "3.+";

    @BeforeAll
    static void init() {
        spec.put(IMAGE_NAME_ALIAS, IMAGE_NAME);
        spec.put(IMAGE_VERSION_ALIAS, IMAGE_VERSION);
        spec.put(VERSION_RANGE_ALIAS, VERSION_RANGE);
    }

    @Test
    void testGetters() {
        assertEquals(IMAGE_NAME, SpecUtils.getImageName(spec));
        assertEquals(IMAGE_VERSION, SpecUtils.getImageVersion(spec));
        assertEquals(VERSION_RANGE, SpecUtils.getImageVersionRange(spec));
    }

    @Test
    void testChange() {
        final String newVersion = "3.5";
        SpecUtils.changeImageVersion(spec, newVersion);
        assertEquals(newVersion, SpecUtils.getImageVersion(spec));
    }
}
