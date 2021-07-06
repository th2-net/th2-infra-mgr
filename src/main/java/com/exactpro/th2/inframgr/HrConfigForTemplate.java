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

package com.exactpro.th2.inframgr;

import com.exactpro.th2.inframgr.errors.NotAcceptableException;
import com.exactpro.th2.inframgr.errors.ServiceException;
import com.exactpro.th2.infrarepo.*;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.io.IOException;

import static com.exactpro.th2.inframgr.SchemaController.REPOSITORY_ERROR;

public class HrConfigForTemplate {

    private final Config config;
    private final String hrName;
    private final String schemaName;

    public HrConfigForTemplate(String hrName, String schemaName) throws IOException {
        config = Config.getInstance();
        this.hrName = hrName;
        this.schemaName = schemaName;
    }

    public String getName() {
        return hrName;
    }

    public Config.CassandraConfig getCassandra() {
        return config.getCassandra() == null ? new Config.CassandraConfig() : config.getCassandra();
    }

    public KeyspaceConfig getKeyspaceConfig() {
        Logger logger = LoggerFactory.getLogger(HrConfigForTemplate.class);

        GitterContext ctx = GitterContext.getContext(config.getGit());
        Gitter gitter = ctx.getGitter(schemaName);
        RepositorySettings settings;
        try {
            gitter.lock();
            settings = Repository.getSnapshot(gitter).getRepositorySettings();
        } catch (RefNotAdvertisedException | RefNotFoundException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.name(), "schema does not exists");
        } catch (Exception e) {
            logger.error("Exception retrieving schema {} from repository", schemaName, e);
            throw new NotAcceptableException(REPOSITORY_ERROR, e.getMessage());
        } finally {
            gitter.unlock();
        }
        return settings == null ? new KeyspaceConfig() : settings.getKeyspaceConfig();
    }
}
