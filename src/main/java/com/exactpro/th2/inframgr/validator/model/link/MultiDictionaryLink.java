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

package com.exactpro.th2.inframgr.validator.model.link;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class MultiDictionaryLink {

    private String name;

    private String box;

    private List<MultiDictionaryDescription> dictionaries;

    public String getName() {
        return this.name;
    }

    public String getBox() {
        return this.box;
    }

    public List<MultiDictionaryDescription> getDictionaries() {
        return dictionaries;
    }

    @Override
    public boolean equals(Object o) {
        throw new AssertionError("method not defined");
    }

    @Override
    public int hashCode() {
        throw new AssertionError("method not defined");
    }

}
