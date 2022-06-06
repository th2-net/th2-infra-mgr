/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.inframgr.util;

import com.exactpro.th2.infrarepo.repo.RepositoryResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/*
    Class takes care of compressing and encoding data of TH2Dictionary
    data if compression is not specified or is false
 */

public class Th2DictionaryProcessor {

    private Th2DictionaryProcessor() {
    }

    private static final Logger logger = LoggerFactory.getLogger(Th2DictionaryProcessor.class);

    private static final String COMPRESSED_KEY = "compressed";

    private static final String DATA_KEY = "data";

    private static String encodeString(String value) throws IOException {

        var baos = new ByteArrayOutputStream();
        var gziposs = new GZIPOutputStream(baos);

        try (baos; gziposs) {
            gziposs.write(value.getBytes());
            gziposs.finish();
            return new String(Base64.getEncoder().encode(baos.toByteArray()));
        }
    }

    public static void compressData(RepositoryResource repositoryResource) {
        if (repositoryResource.getSpec() instanceof Map) {
            try {
                Map<String, Object> specMap = (Map<String, Object>) repositoryResource.getSpec();
                if (!specMap.containsKey(COMPRESSED_KEY) || specMap.get(COMPRESSED_KEY).toString().equals("false")) {
                    String stringedData = specMap.get(DATA_KEY).toString();
                    specMap.put(DATA_KEY, encodeString(stringedData));
                    specMap.put(COMPRESSED_KEY, true);
                    repositoryResource.setSpec(specMap);
                }
            } catch (Exception e) {
                logger.error("Could not compress dictionary \"{}\"}",
                        repositoryResource.getMetadata().getName(),
                        e);
            }

            return;
        }

        logger.warn("Dictionary \"{}\" doesn't have full specs, skipping compression part",
                repositoryResource.getMetadata().getName());
    }
}
