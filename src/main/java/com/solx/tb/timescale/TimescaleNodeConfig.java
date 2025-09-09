/**
 * Copyright © 2018-2025 The Thingsboard Authors
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
package com.solx.tb.timescale;

import org.thingsboard.rule.engine.api.NodeConfiguration;

public class TimescaleNodeConfig implements NodeConfiguration<TimescaleNodeConfig> {
    // Simple, textbox-like fields users edit in TB UI:
    public String host;
    public int    port;
    public String db;
    public String schema;
    public String table;
    public String user;
    public String password;

    /** Optional: "disable", "require", "verify-ca", or "verify-full" */
    public String sslMode;

    /** Internal: built in init(); used by onMsg() */
    public String insertSql;

    @Override
    public TimescaleNodeConfig defaultConfiguration() {
        TimescaleNodeConfig c = new TimescaleNodeConfig();
        c.host = "localhost";
        c.port = 5432;
        c.db = "iot";
        c.schema = "public";
        c.table = "telemetry";
        c.user = "postgres";
        c.password = "postgres";
        c.sslMode = "disable";
        c.insertSql = null; // set during node init
        return c;
    }
}
