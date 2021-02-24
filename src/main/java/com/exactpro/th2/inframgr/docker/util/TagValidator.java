package com.exactpro.th2.inframgr.docker.util;

import java.util.List;
import java.util.stream.Collectors;

public class TagValidator {

    private TagValidator(){
        throw new AssertionError("This method should not be called");
    }

    public static boolean validate(String tags, String pattern){
        //TODO validation logic
        return true;
    }

    public static List<String> filteredTags(List<String> tags, String mask) {
        return tags.stream()
                .filter(tag -> TagValidator.validate(tag, mask))
                .collect(Collectors.toList());
    }

    public static String getLatestTag(List<String> tags) {
        //TODO check if list is in correct order, if not add logic to find latest tag
        return tags.get(tags.size() - 1);
    }
}
