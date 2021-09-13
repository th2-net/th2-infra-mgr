package com.exactpro.th2.inframgr.validator;

import com.exactpro.th2.inframgr.validator.cache.SchemaValidationTable;
import com.exactpro.th2.inframgr.validator.enums.ValidationStatus;
import com.exactpro.th2.infrarepo.RepositoryResource;

import java.util.ArrayList;
import java.util.Map;

public class SchemaContext {
    private final String schemaName;
    private String commitRef;
    private final Map<String, RepositoryResource> allBoxes;
    private final Map<String, RepositoryResource> dictionaries;
    private SchemaValidationTable schemaValidationTable;


    public SchemaContext(String schemaName, String commitRef,
                         Map<String, RepositoryResource> allBoxes,
                         Map<String, RepositoryResource> dictionaries,
                         SchemaValidationTable schemaValidationTable) {
        this.schemaName = schemaName;
        this.commitRef = commitRef;
        this.allBoxes = allBoxes;
        this.dictionaries = dictionaries;
        this.schemaValidationTable = schemaValidationTable;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getCommitRef() {
        return commitRef;
    }

    public RepositoryResource getBox(String boxName) {
        return allBoxes.get(boxName);
    }

    public RepositoryResource getDictionary(String dictionaryName) {
        return dictionaries.get(dictionaryName);
    }

    public SchemaValidationTable getSchemaValidationTable() {
        return schemaValidationTable;
    }

    private static class ResourcesList {
        private ValidationStatus status = ValidationStatus.VALID;
        private final ArrayList<RepositoryResource> linkedResources = new ArrayList<>();
    }
}
