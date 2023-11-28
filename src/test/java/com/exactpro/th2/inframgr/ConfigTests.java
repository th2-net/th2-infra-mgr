/*
 * Copyright 2023 Exactpro (Exactpro Systems Limited)
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

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigTests {

    @Test
    public void invalidHttpPassTest() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> Config.readConfiguration(
                Path.of("src", "test", "resources", "config", "invalid_http_pass.yaml")));

        assertEquals("'adminAccounts' has the problems: [" +
                "Password for admin user \"raw\" is incorrect by reason: Encoded *** does not look like BCrypt: ***, " +
                "Upgrade password for admin user \"strength_9\" is required]", exception.getMessage());
    }
}
