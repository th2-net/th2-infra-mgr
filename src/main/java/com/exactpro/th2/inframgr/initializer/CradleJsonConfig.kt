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

package com.exactpro.th2.inframgr.initializer

import com.exactpro.th2.infrarepo.CradleConfig

data class CradleJsonConfig(
    var keyspace: String,
    var host: String,
    var port: Int,
    var dataCenter: String,
    var username: String,
    var password: String
) {
    fun overwriteWith(customConfig: CradleConfig) {
        this.keyspace = customConfig.keyspace ?: DEFAULT_KEYSPACE
        if (customConfig.host != null) {
            this.host = customConfig.host
        }
        if (customConfig.port != 0) {
            this.port = customConfig.port
        }
        if (customConfig.dataCenter != null) {
            this.dataCenter = customConfig.dataCenter
        }
        if (customConfig.username != null) {
            this.username = customConfig.username
        }
        if (customConfig.isUseCustomPassword) {
            this.password = USER_CASSANDRA_PASSWORD
        }
    }

    companion object {
        private const val DEFAULT_KEYSPACE = "cradle_info"
        private const val USER_CASSANDRA_PASSWORD = "\${USER_CASSANDRA_PASS}"
    }
}
