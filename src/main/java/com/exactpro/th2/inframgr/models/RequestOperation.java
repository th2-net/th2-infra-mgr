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

package com.exactpro.th2.inframgr.models;

import java.util.HashMap;
import java.util.Map;

public enum RequestOperation {
    add("add"),
    update("update"),
    remove("remove");

    private String type;

    RequestOperation(String type) {
        this.type = type;
    }
    public String type() {
        return type;
    }

    public static RequestOperation forType(String type) {
        return types.get(type);
    }

    private static Map<String, RequestOperation> types = new HashMap<>();

    static {
        for (RequestOperation t : RequestOperation.values()) {
            types.put(t.type(), t);
        }
    }
}
