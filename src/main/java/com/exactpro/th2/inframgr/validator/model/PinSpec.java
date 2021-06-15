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

package com.exactpro.th2.inframgr.validator.model;

import com.exactpro.th2.inframgr.validator.enums.SchemaConnectionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class PinSpec {

    protected String name;

    @JsonProperty("connection-type")
    protected SchemaConnectionType connectionType;

    protected Set<String> attributes = new HashSet<>();

    public String getName() {
        return name;
    }

    public SchemaConnectionType getConnectionType() {
        return connectionType;
    }

    public Set<String> getAttributes() {
        return attributes;
    }
}
