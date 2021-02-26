package com.exactpro.th2.inframgr.docker.util;

import java.util.List;
import java.util.stream.Collectors;

public class VersionNumberUtils {
    private static final char PLUS_CHAR = '+';

    private VersionNumberUtils(){
    }

    public static boolean validate(String tag, String versionRange){
        return tag.startsWith(versionRange);
    }

    public static List<String> filterTags(List<String> tags, String versionRange) {
        return tags.stream()
                .filter(tag -> VersionNumberUtils.validate(tag, versionRange))
                .collect(Collectors.toList());
    }

    public static String chooseLatestVersion(List<String> tags) {
        //TODO logic to find latest tag
        return "7.7.7";
    }

    public static String trimVersionRange(String versionRange){
        return versionRange.substring(0, versionRange.indexOf(PLUS_CHAR));
    }
}
