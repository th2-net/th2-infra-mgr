package com.exactpro.th2.inframgr.docker;

import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Secret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecretMapper {
    private static final Logger logger = LoggerFactory.getLogger(SecretMapper.class);

    private static final String SECRET_DATA_ALIAS = ".dockerconfigjson";
    private static final String AUTHS_ALIAS = "auths";
    private static final String USER_ALIAS = "username";
    private static final String PASSWORD_ALIAS = "password";
    private static final String AUTHENTICATION_STRING_ALIAS = "auth";
    private static final String SEPARATOR = ":";


    private final Kubernetes kube;
    private final ObjectMapper mapper;

    public SecretMapper(Kubernetes kube, ObjectMapper mapper) {
        this.kube = kube;
        this.mapper = mapper;
    }

    public Map<String, AuthenticationDetails> mapSecrets() {
        List<Secret> secrets = kube.getRegistrySecrets();
        Map<String, AuthenticationDetails> secretsMap = new HashMap<>();
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
                    AuthenticationDetails credentials = getCredentials(entry.getValue());
                    secretsMap.put(key, credentials);
                }
            } catch (Exception e) {
                logger.warn("Exception while decoding secret: \"{}\"", secret.getMetadata().getName());
            }
        }
        return secretsMap;
    }

    private AuthenticationDetails getCredentials(Object entryValue) {
        Map<String, String> credentials = mapper.convertValue(entryValue, Map.class);
        if (credentials.containsKey(USER_ALIAS) && credentials.containsKey(PASSWORD_ALIAS)) {
            return new AuthenticationDetails(
                    credentials.get(USER_ALIAS),
                    credentials.get(PASSWORD_ALIAS)
            );
        } else if (credentials.containsKey(AUTHENTICATION_STRING_ALIAS)) {
            String authStr = credentials.get(AUTHENTICATION_STRING_ALIAS);
            String authStrDecoded = new String(Base64.getDecoder().decode(authStr));
            return new AuthenticationDetails(
                    authStrDecoded.substring(0, authStrDecoded.indexOf(SEPARATOR)),
                    authStrDecoded.substring(authStrDecoded.indexOf(SEPARATOR) + 1)
            );
        } else return null;
    }

    public static class AuthenticationDetails {
        private final String user;
        private final String password;

        public AuthenticationDetails(String user, String password) {
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
