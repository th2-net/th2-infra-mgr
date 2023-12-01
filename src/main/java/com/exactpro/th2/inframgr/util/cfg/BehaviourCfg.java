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

package com.exactpro.th2.inframgr.util.cfg;

public class BehaviourCfg {
    /**
     * Has infra-mgr got permission to remove Kubernetes namespace when
     * branch is disabled (spec.k8s-propagation: deny) or removed.
     * Infra-manager removes Kubernetes namespace when this option is true otherwise
     * stops maintenance for the namespace without deleting any resources.
     * Maintenance is continued when user enable or create the branch related to namespace: `<prefix><branch name>`
     */
    private boolean permittedToRemoveNamespace = true;

    public boolean isPermittedToRemoveNamespace() {
        return permittedToRemoveNamespace;
    }

    public void setPermittedToRemoveNamespace(boolean permittedToRemoveNamespace) {
        this.permittedToRemoveNamespace = permittedToRemoveNamespace;
    }
}
