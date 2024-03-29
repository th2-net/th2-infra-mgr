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

import com.exactpro.th2.inframgr.models.ResourceEntry;
import com.exactpro.th2.infrarepo.repo.RepositorySnapshot;
import com.exactpro.th2.validator.ValidationReport;

import java.util.HashSet;
import java.util.Set;

public class SchemaControllerResponse {

    private String commitRef;

    private Set<ResourceEntry> resources;

    private ValidationReport validationErrors;

    public SchemaControllerResponse(ValidationReport validationErrors) {
        this.validationErrors = validationErrors;
        this.resources = null;
    }

    public SchemaControllerResponse(RepositorySnapshot snapshot) {
        this.commitRef = snapshot.getCommitRef();
        this.resources = new HashSet<>();
        snapshot.getResources().forEach(e -> resources.add(new ResourceEntry(e)));
        this.validationErrors = null;
    }

    public String getCommitRef() {
        return commitRef;
    }

    public Set<ResourceEntry> getResources() {
        return resources;
    }

    public ValidationReport getValidationErrors() {
        return validationErrors;
    }
}
