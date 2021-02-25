package com.exactpro.th2.inframgr.docker.util;

import java.util.List;
import java.util.stream.Collectors;

public class TagValidator {

    private TagValidator(){
    }

    public static boolean validate(String tags, String mask){
        //TODO validation logic
        return true;
    }

    public static List<String> filterTags(List<String> tags, String mask) {
        return tags.stream()
                .filter(tag -> TagValidator.validate(tag, mask))
                .collect(Collectors.toList());
    }

    public static String getLatestTag(List<String> tags) {
        //TODO logic to find latest tag
        return "";
    }
}
