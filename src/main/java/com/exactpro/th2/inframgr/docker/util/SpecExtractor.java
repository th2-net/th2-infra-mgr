package com.exactpro.th2.inframgr.docker.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SpecExtractor {
    private static final String SPLIT_CHARACTER = "\\.";
    private static final String SEPARATOR = ".";

    private static final ObjectMapper mapper = new ObjectMapper(new JsonFactory());

    public static String getFieldAsString(Object sourceObj, String path) {
        String[] fields = path.split(SPLIT_CHARACTER);
        JsonNode currentField = mapper.convertValue(sourceObj, JsonNode.class);
        for (String field : fields) {
            currentField = currentField.get(field);
            if (currentField == null) {
                return null;
            }
        }
        return currentField.toString();
    }
}
