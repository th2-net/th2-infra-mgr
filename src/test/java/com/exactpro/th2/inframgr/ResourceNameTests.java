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

import com.exactpro.th2.inframgr.k8s.K8sCustomResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceNameTests {

    @Test
    void testValidNames() {
        String[] names = new String[]{
                "test-123-ds",
                "test-123",
                "123tesz-123",
                "00000",
                "a-b-74-5",
                "n"
        };
        for (String name : names) {
            assertTrue(K8sCustomResource.isNameValid(name), name);
        }
    }

    @Test
    void testInvalidValidNames() {
        String[] names = new String[]{
                "-test-123-ds",
                "test-123-",
                "123teAasz-123",
                "sada.asd",
                "a-b-74-",
                "-",
                "x!^+",
                "y?",
                "9$%&*()",
                ""
        };
        for (String name : names) {
            assertFalse(K8sCustomResource.isNameValid(name), name);
        }
    }

    @Test
    void testLongNames() {
        String barelyLegal = "x".repeat(K8sCustomResource.RESOURCE_NAME_MAX_LENGTH - 1);
        String illegal = barelyLegal + 'x';

        assertTrue(K8sCustomResource.isNameValid(barelyLegal), barelyLegal);
        assertFalse(K8sCustomResource.isNameValid(illegal), illegal);
    }
}
