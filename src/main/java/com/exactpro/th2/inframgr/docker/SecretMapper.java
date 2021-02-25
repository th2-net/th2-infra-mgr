package com.exactpro.th2.inframgr.docker;

import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Secret;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecretMapper {

    private static final String SECRET_DATA_ALIAS = ".dockerconfigjson";
    private static final String AUTHS_ALIAS = "auths";
    private static final String AUTHENTICATION_STRING_ALIAS = "auth";
    private static final String SEPARATOR = ":";


    private Kubernetes kube;
    private ObjectMapper mapper;

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
                JsonNode authData = mapper.readTree(data).get(AUTHS_ALIAS);
                //get registry name
                String registry = authData.fieldNames().next();
                //get encoded authentication string
                String authStr = authData.get(registry).get(AUTHENTICATION_STRING_ALIAS).toString();
                //remove extra " characters
                authStr = authStr.substring(1, authStr.length() - 1);
                //decode string that contains username and password
                String authStrDecoded = new String(Base64.getDecoder().decode(authStr));
                AuthenticationDetails authenticationDetails = new AuthenticationDetails(
                        authStrDecoded.substring(0, authStrDecoded.indexOf(SEPARATOR)),
                        authStrDecoded.substring(authStrDecoded.indexOf(SEPARATOR) + 1)
                );
                secretsMap.put(registry, authenticationDetails);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return secretsMap;
    }

    public static class AuthenticationDetails {
        private String user;
        private String password;

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
