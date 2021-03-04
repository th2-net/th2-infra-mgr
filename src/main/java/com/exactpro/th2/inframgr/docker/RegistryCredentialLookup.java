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

package com.exactpro.th2.inframgr.docker;

import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Secret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegistryCredentialLookup {
    private static final Logger logger = LoggerFactory.getLogger(RegistryCredentialLookup.class);

    private static final String SECRET_DATA_ALIAS = ".dockerconfigjson";
    private static final String AUTHS_ALIAS = "auths";
    private static final String USER_ALIAS = "username";
    private static final String PASSWORD_ALIAS = "password";
    private static final String AUTHENTICATION_STRING_ALIAS = "auth";
    private static final String SEPARATOR = ":";


    private final Kubernetes kube;
    private final ObjectMapper mapper;

    public RegistryCredentialLookup(Kubernetes kube) {
        this.kube = kube;
        this.mapper = new ObjectMapper();
    }

    public Map<String, RegistryCredentials> getCredentials() {
        List<Secret> secrets = kube.getDockerRegistrySecrets();
        Map<String, RegistryCredentials> secretsMap = new HashMap<>();
        for (Secret secret : secrets) {
            //extract data part from secrets
            String data = new String(Base64.getDecoder().decode(secret.getData().get(SECRET_DATA_ALIAS)));
            //extract authentication object from data
            try {
                //extract 'auths' map
                Map<String, Object> firstLevel = mapper.readValue(data, Map.class);
                var secondLevel = firstLevel.get(AUTHS_ALIAS);
                //extract repository mapping from 'auths'
                Map<String, Object> authMap = mapper.convertValue(secondLevel, Map.class);
                //for each repository extract credentials
                for (var entry : authMap.entrySet()) {
                    String key = entry.getKey();
                    RegistryCredentials credentials = getCredentials(entry.getValue());
                    secretsMap.put(key, credentials);
                }
            } catch (Exception e) {
                logger.error("Exception while decoding secret: \"{}\"", secret.getMetadata().getName());
            }
        }
        return secretsMap;
    }

    private RegistryCredentials getCredentials(Object entryValue) {
        Map<String, String> credentials = mapper.convertValue(entryValue, Map.class);
        if (credentials.containsKey(USER_ALIAS) && credentials.containsKey(PASSWORD_ALIAS)) {
            return new RegistryCredentials(
                    credentials.get(USER_ALIAS),
                    credentials.get(PASSWORD_ALIAS)
            );
        } else if (credentials.containsKey(AUTHENTICATION_STRING_ALIAS)) {
            String authStr = credentials.get(AUTHENTICATION_STRING_ALIAS);
            String authStrDecoded = new String(Base64.getDecoder().decode(authStr));
            return new RegistryCredentials(
                    authStrDecoded.substring(0, authStrDecoded.indexOf(SEPARATOR)),
                    authStrDecoded.substring(authStrDecoded.indexOf(SEPARATOR) + 1)
            );
        } else return null;
    }

    public static class RegistryCredentials {
        private final String user;
        private final String password;

        public RegistryCredentials(String user, String password) {
            this.user = user;
            this.password = password;
        }

        public String getUser() {
            return user;
        }

        public String getPassword() {
            return password;
        }
    }
}
