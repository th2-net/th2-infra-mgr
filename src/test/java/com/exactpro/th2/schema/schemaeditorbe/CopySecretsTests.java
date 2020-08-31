package com.exactpro.th2.schema.schemaeditorbe;


import com.exactpro.th2.schema.schemaeditorbe.k8s.Kubernetes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CopySecretsTests {

    private static final Logger logger = LoggerFactory.getLogger(CopySecretsTests.class);


    private ObjectMapper om = new ObjectMapper(new YAMLFactory());


    @BeforeAll
    public void setup() throws Exception {
        logger.info("Setup tests...");
    }

    @BeforeEach
    public void beforeEach() throws Exception {

    }


    @Test
    public void copySecrets() throws Exception {

        var schemaNamespace = "first-project";

        clearSecrets("schema-" + schemaNamespace);

        var k = new Kubernetes(new Config.K8sConfig(), schemaNamespace);

        assertEquals(5, k.copySecrets().size());
    }


    @AfterAll
    public void after() throws Exception {

    }


    private void clearSecrets(String targetNamespace) throws IOException {
        var client = new DefaultKubernetesClient();

        var currentSecrets = client.secrets().list().getItems();

        for (var secretName : Config.getInstance().getKubernetes().getSecretNames()) {
            var secret = currentSecrets.stream()
                    .filter(s -> s.getMetadata().getName().equals(secretName))
                    .findFirst()
                    .orElse(null);

            if (Objects.nonNull(secret)) {
                secret.getMetadata().setResourceVersion(null);
                secret.getMetadata().setNamespace(targetNamespace);
                client.secrets().inNamespace(targetNamespace).delete(secret);
            }
        }
    }

}
