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

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.exactpro.th2.inframgr.BasicAuthConfig.PASSWORD_ENCODER;

public class HttpCfg {

    /**
     * Map of username to encrypted by BCrypt (strength >= 10) password pairs.
     * @see <a href="https://en.wikipedia.org/wiki/Bcrypt">BCrypt</a>
     */
    private Map<String, String> adminAccounts;

    public Map<String, String> getAdminAccounts() {
        return adminAccounts;
    }

    public void setAdminAccounts(Map<String, String> adminAccounts) {
        this.adminAccounts = adminAccounts;
    }

    public void validate() {
        if (adminAccounts.isEmpty()) {
            throw new IllegalStateException("'adminAccounts' mustn't be empty");
        }

        List<String> passwordProblems = adminAccounts.entrySet().stream()
                .map(entry -> {
                    try {
                        if (PASSWORD_ENCODER.upgradeEncoding(entry.getValue())) {
                            return "Upgrade password for admin user \"" + entry.getKey() + "\" is required";
                        }
                    } catch (RuntimeException e) {
                        return "Password for admin user \"" + entry.getKey() + "\" is incorrect by reason: " +
                                StringUtils.replace(e.getMessage(), entry.getValue(), "***");
                    }
                    return null;
                }).filter(Objects::nonNull)
                .toList();

        if (!passwordProblems.isEmpty()) {
            throw new IllegalStateException("'adminAccounts' has the problems: " + passwordProblems);
        }
    }
}
