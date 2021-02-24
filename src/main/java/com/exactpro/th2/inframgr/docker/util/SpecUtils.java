package com.exactpro.th2.inframgr.docker.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class SpecUtils {
    private static final Logger logger = LoggerFactory.getLogger(SpecUtils.class);

    private static final String SPLIT_CHARACTER = "\\.";
    private static final String SEPARATOR = ".";

    private static final String IMAGE_NAME_ALIAS = "image-name";
    private static final String IMAGE_VERSION_ALIAS = "image-version";
    private static final String VERSION_RANGE_ALIAS = "version-range";

    private static final ObjectMapper mapper = new ObjectMapper(new JsonFactory());

    public static String getImageName(Object sourceObj){
        return getFieldAsString(sourceObj, IMAGE_NAME_ALIAS);
    }

    public static String getImageVersion(Object sourceObj){
        return getFieldAsString(sourceObj, IMAGE_VERSION_ALIAS);
    }

    public static String getImageVersionRange(Object sourceObj){
        return getFieldAsString(sourceObj, VERSION_RANGE_ALIAS);
    }

    public static void changeImageVersion(Object spec, String imageVersion){
        try {
            Map<String, Object> specMap = (Map<String, Object>) spec;
            specMap.put(IMAGE_VERSION_ALIAS, imageVersion);
        }catch (ClassCastException e){
            logger.error("Couldn't cast provided spec to map", e);
        }
    }

    private static String getFieldAsString(Object sourceObj, String path) {
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
