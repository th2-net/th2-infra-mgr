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

package com.exactpro.th2.inframgr.cassandra.template;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.cassandra.template.helmrelease.Component;
import com.exactpro.th2.inframgr.cassandra.template.helmrelease.Keyspace;
import com.exactpro.th2.inframgr.initializer.SchemaInitializer;

import java.io.IOException;

public class HrConfigForTemplate {

    private final Config config;
    private final String hrName;
    private final Keyspace keyspace;
    private final Component component;

    public HrConfigForTemplate(String hrName, String schemaName, String imageName, String imageVersion) throws IOException {
        config = Config.getInstance();
        this.hrName = hrName;
        this.keyspace = SchemaInitializer.getKeyspaceMap().get(schemaName);
        this.component = new Component(imageName, imageVersion);
    }

    public String getName() {
        return hrName;
    }

    public Config.CassandraConfig getCassandra() {
        return config.getCassandra() == null ? new Config.CassandraConfig() : config.getCassandra();
    }

    public Keyspace getKeyspace() {
        return keyspace == null ? new Keyspace() : keyspace;
    }

    public Component getComponent(){
        return component;
    }
}
