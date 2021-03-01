package com.exactpro.th2.inframgr.docker.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class VersionNumberUtils {
    private static final String SPLIT_CHAR = "\\.";

    private VersionNumberUtils() {
    }

    public static boolean validate(String tag, String versionRange) {
        return tag.startsWith(versionRange);
    }

    public static List<String> filterTags(List<String> tags, String versionRange) {
        //choose only tags that follow versionRange map, and extract
        return tags.stream()
                .filter(tag -> VersionNumberUtils.validate(tag, versionRange))
                .map(tag -> tag.substring(versionRange.length()))
                .collect(Collectors.toList());
    }

    public static String chooseLatestVersion(List<String> tags) {
        if (tags.size() < 1) {
            return null;
        }
        //convert tag strings to tag objects
        List<TagObject> tagObjects = toTagObjects(tags);
        if (tagObjects.size() < 1) {
            return null;
        }
        if (tagObjects.size() == 1) {
            return tagObjects.get(0).tag;
        }
        TagObject current = tagObjects.get(0);
        for (int i = 1; i < tagObjects.size(); i++) {
            current = compare(current, tagObjects.get(i));
        }
        return current.tag;
    }


    private static TagObject compare(TagObject current, TagObject other) {
        if (current.integerParts.size() <= other.integerParts.size()) {
            return compareLists(current, other);
        } else {
            return compareLists(other, current);
        }
    }

    private static TagObject compareLists(TagObject first, TagObject second) {
        for (int i = 0; i < first.integerParts.size(); i++) {
            if (first.integerParts.get(i) > second.integerParts.get(i)) {
                return first;
            } else if (first.integerParts.get(i) < second.integerParts.get(i)) {
                return second;
            }
        }
        return second;
    }

    private static List<TagObject> toTagObjects(List<String> tags) {
        List<TagObject> tagObjects = new ArrayList<>();
        //convert integer part of tag string into array of integers.
        for (String tag : tags) {
            boolean isValid = true;
            String[] tagPieces = tag.split(SPLIT_CHAR);
            List<Integer> integerParts = new ArrayList<>();
            for (String tagPiece : tagPieces) {
                try {
                    integerParts.add(Integer.parseInt(tagPiece));
                } catch (NumberFormatException e) {
                    isValid = false;
                    break;
                }
            }
            if (isValid) {
                tagObjects.add(new TagObject(tag, integerParts));
            }
        }
        return tagObjects;
    }

    private static class TagObject {
        private final String tag;
        private final List<Integer> integerParts;

        public TagObject(String tag, List<Integer> integerParts) {
            this.tag = tag;
            this.integerParts = integerParts;
        }
    }

}
