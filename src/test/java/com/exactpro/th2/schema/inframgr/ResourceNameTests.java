/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.schema.inframgr;


import com.exactpro.th2.schema.inframgr.k8s.K8sCustomResource;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceNameTests {
    Pattern pattern;

    public ResourceNameTests() {
        pattern = Pattern.compile(K8sCustomResource.RESOURCE_NAME_REGEXP);
    }

    @Test
    void TestValidNames() {
        String[] names = new String[]{
                "test-123-ds",
                "test-123",
                "123tesz-123",
                "00000",
                "a-b-74-5",
                "n"
        };
        for (String name: names) {
            assertTrue(pattern.matcher(name).matches(),  name);
        }
    }

    @Test
    void TestInvalidValidNames() {
        String[] names = new String[]{
                "-test-123-ds",
                "test-123-",
                "123teAasz-123",
                "sada.asd",
                "a-b-74-",
                "-",
                ""
        };
        for (String name: names) {
            assertFalse(pattern.matcher(name).matches(),  name);
        }
    }
}