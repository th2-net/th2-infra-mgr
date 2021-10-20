package com.exactpro.th2.inframgr.validator.enums;

public enum MessageFormatAttribute {
    raw("raw"),
    parsed("parsed"),
    group("group"),
    event("event");

    private String prefix;

    MessageFormatAttribute(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
