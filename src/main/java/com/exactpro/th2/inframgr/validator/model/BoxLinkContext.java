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

import com.exactpro.th2.inframgr.validator.enums.BoxDirection;
import com.exactpro.th2.inframgr.validator.enums.SchemaConnectionType;
import com.exactpro.th2.infrarepo.RepositoryResource;
import lombok.Builder;

@Builder(toBuilder = true)
public final class BoxLinkContext {

    private final String boxName;

    private final String boxPinName;

    private final BoxDirection boxDirection;

    private final SchemaConnectionType connectionType;

    private RepositoryResource linkedResource;

    private String linkedPinName;

    public String getBoxName() {
        return boxName;
    }

    public String getBoxPinName() {
        return boxPinName;
    }

    public BoxDirection getBoxDirection() {
        return boxDirection;
    }

    public SchemaConnectionType getConnectionType() {
        return connectionType;
    }

    public RepositoryResource getLinkedResource() {
        return linkedResource;
    }

    public String getLinkedPinName() {
        return linkedPinName;
    }
}