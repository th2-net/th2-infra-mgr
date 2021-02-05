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

package com.exactpro.th2.inframgr.util.cfg;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class _RabbitMQConfig {
    private String secret = "rabbitmq";
    private String vhostPrefix;
    private String usernamePrefix;
    private Integer passwordLength = 16;
    private String passwordChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        if (secret != null)
            this.secret = secret;
    }

    public String getVhostPrefix() {
        return vhostPrefix == null ? "" : vhostPrefix;
    }

    public void setVhostPrefix(String vhostPrefix) {
        this.vhostPrefix = vhostPrefix;
    }

    public String getUsernamePrefix() {
        return usernamePrefix == null ? getVhostPrefix() : usernamePrefix;
    }

    public void setUsernamePrefix(String usernamePrefix) {
        this.usernamePrefix = usernamePrefix;
    }

    public Integer getPasswordLength() {
        return passwordLength;
    }

    public void setPasswordLength(Integer passwordLength) {
        if (passwordLength != null)
            this.passwordLength = passwordLength;
    }

    public String getPasswordChars() {
        return passwordChars;
    }

    public void setPasswordChars(String passwordChars) {
        if (passwordChars != null)
            this.passwordChars = passwordChars.trim();
    }
}
