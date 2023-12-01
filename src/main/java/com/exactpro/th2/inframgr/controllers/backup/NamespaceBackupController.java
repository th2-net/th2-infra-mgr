/*
 * Copyright 2020-2023 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.inframgr.controllers.backup;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.errors.NotAcceptableException;
import com.exactpro.th2.inframgr.k8s.K8sCustomResource;
import com.exactpro.th2.inframgr.k8s.SecretsManager;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.LocalFileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.commons.text.RandomStringGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.*;

import static java.lang.String.format;

@Controller
public class NamespaceBackupController {

    private static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";

    private static final int PASSWORD_LENGTH = 20;

    private static final String BAD_RESOURCE_NAME = "BAD_RESOURCE_NAME";

    private static final String CUSTOM_SECRETS_SUFFIX = "custom-secrets";

    private static final Logger logger = LoggerFactory.getLogger(NamespaceBackupController.class);

    @Autowired
    private Config config;

    ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .registerModule(new KotlinModule.Builder().build());

    @GetMapping("/backup/{schemaName}")
    @ResponseBody
    public BackupObject getBackupZip(@PathVariable(name = "schemaName") String schemaName) throws IOException {
        if (!K8sCustomResource.isSchemaNameValid(schemaName)) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");
        }

        logger.info("Preparing backup for schema: \"{}\"", schemaName);
        List<ContentToZip> zipContents = new ArrayList<>();
        String password = generateRandomPassword(schemaName);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(out, password.toCharArray());

        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setEncryptFiles(true);
        zipParameters.setEncryptionMethod(EncryptionMethod.AES);
        zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

        // add custom-secrets-config as zip content
        SecretsManager secretsManager = new SecretsManager(config.getKubernetes().getNamespacePrefix());
        var secret = secretsManager.getCustomSecret(schemaName);
        if (secret == null || secret.getData() == null) {
            logger.info("There are no custom secrets present in schema: \"{}\"", schemaName);
        } else {
            logger.info("Adding custom secrets to backup for schema: \"{}\"", schemaName);
            String secretContent = mapper.writeValueAsString(secret.getData());
            String fileName = format("%s-%s", schemaName, CUSTOM_SECRETS_SUFFIX);
            zipContents.add(new ContentToZip(secretContent, fileName));
        }

        for (var zipContent : zipContents) {
            byte[] data = zipContent.getData().getBytes();
            zipParameters.setFileNameInZip(format("%s.txt", zipContent.getFileName()));
            zipOutputStream.putNextEntry(zipParameters);
            zipOutputStream.write(data, 0, data.length);
            zipOutputStream.closeEntry();
        }
        zipOutputStream.close();
        try (out) {
            return new BackupObject(password, new String(Base64.getEncoder().encode(out.toByteArray())));
        }
    }

    @PutMapping("/backup/{schemaName}")
    @ResponseBody
    public BackupResponse putBackupZip(@PathVariable(name = "schemaName") String schemaName,
                                       @RequestBody String requestBody) throws IOException {
        if (!K8sCustomResource.isSchemaNameValid(schemaName)) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");
        }
        logger.info("Applying backup for schema: \"{}\"", schemaName);
        BackupObject backupObject = mapper.readValue(requestBody, new TypeReference<>() {
        });

        BackupResponse backupResponse = new BackupResponse();

        byte[] zipContent = Base64.getDecoder().decode(backupObject.getContent().getBytes());
        ByteArrayInputStream in = new ByteArrayInputStream(zipContent);
        ZipInputStream zipInputStream = new ZipInputStream(in, backupObject.getPassword().toCharArray());
        BufferedReader reader = new BufferedReader(new InputStreamReader(zipInputStream));
        LocalFileHeader entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            String entryName = entry.getFileName();
            logger.info("Processing: \"{}\" for: \"{}\"", entryName, schemaName);
            if (entryName.contains(CUSTOM_SECRETS_SUFFIX)) {
                String content = reader.readLine();
                if (content != null) {
                    SecretsManager secretsManager = new SecretsManager(config.getKubernetes().getNamespacePrefix());
                    Map<String, String> secretEntries = mapper.readValue(content, new TypeReference<>() {
                    });
                    backupResponse.addCustomSecrets(secretsManager.createOrReplaceSecrets(schemaName, secretEntries));
                }
            }
        }
        return backupResponse;
    }

    private static String generateRandomPassword(String content) {
        RandomStringGenerator pwdGenerator = new RandomStringGenerator.Builder()
                .selectFrom(content.toCharArray())
                .build();
        return pwdGenerator.generate(PASSWORD_LENGTH).replaceAll("\\s", "q");
    }
}
