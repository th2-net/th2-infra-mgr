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

package com.exactpro.th2.inframgr.controllers.backup;

import java.util.HashSet;
import java.util.Set;

public class BackupResponse {
    private final Set<String> customSecrets;

    public BackupResponse() {
        customSecrets = new HashSet<>();
    }

    public Set<String> getCustomSecrets() {
        return customSecrets;
    }

    public void addCustomSecrets(Set<String> customSecrets) {
        this.customSecrets.addAll(customSecrets);
    }
}
