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

package com.exactpro.th2.inframgr.helmRelease;

import com.exactpro.th2.infrarepo.RepositoryResource;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HrResource extends RepositoryResource {

    public String getApiVersion() {
        return super.getApiVersion() == null ? "" : super.getApiVersion();
    }

    public String getKind() {
        return super.getKind() == null ? "" : super.getKind();
    }

    public HrMetadata getMetadata() {
        return super.getMetadata() == null ? new HrMetadata("") : (HrMetadata) super.getMetadata();
    }

    public void setMetadata(HrMetadata hrMetadata) {
        super.setMetadata(hrMetadata);
    }

    public Spec getSpec() {
        return super.getSpec() == null ? new Spec() : (Spec) super.getSpec();
    }

    public void setSpec(Spec spec) {
        super.setSpec(spec);
    }
}
