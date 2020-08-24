package com.exactpro.th2.schema.schemaeditorbe;


import com.exactpro.th2.schema.schemaeditorbe.k8s.K8sCustomResource;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceNameTests {
    Pattern pattern;

    public ResourceNameTests() {
        pattern = Pattern.compile(K8sCustomResource.RESOURCE_NAME_REGEXP);
    }

    @Test
    void TestValidNames() {
        String[] names = new String[]{
                "test-123-ds",
                "test-123",
                "123tesz-123",
                "00000",
                "a-b-74-5",
                "n"
        };
        for (String name: names) {
            assertTrue(pattern.matcher(name).matches(),  name);
        }
    }

    @Test
    void TestInvalidValidNames() {
        String[] names = new String[]{
                "-test-123-ds",
                "test-123-",
                "123teAasz-123",
                "sada.asd",
                "a-b-74-",
                "-",
                ""
        };
        for (String name: names) {
            assertFalse(pattern.matcher(name).matches(),  name);
        }
    }
}