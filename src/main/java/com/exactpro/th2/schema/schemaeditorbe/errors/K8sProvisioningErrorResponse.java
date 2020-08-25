package com.exactpro.th2.schema.schemaeditorbe.errors;

import com.exactpro.th2.schema.schemaeditorbe.models.ResourceEntry;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

public class K8sProvisioningErrorResponse extends ErrorResponse {
    private class Item {
        @JsonProperty
        private String kind;
        @JsonProperty
        private String name;
    }
    @JsonProperty
    private List<Item> items;

    public K8sProvisioningErrorResponse(HttpStatus httpStatus, String errorCode, String message) {
        super(httpStatus, errorCode, message);
        items = new ArrayList<>();
    }

    public void addItem(ResourceEntry entry) {
        Item item = new Item();
        item.kind = entry.getKind().kind();
        item.name = entry.getName();
        items.add(item);
    }
}
