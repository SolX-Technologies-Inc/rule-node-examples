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

import com.fasterxml.jackson.databind.JsonNode;
import org.thingsboard.common.util.JacksonUtil;

import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;

import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Iterator;

@RuleNode(
        type = ComponentType.ACTION,
        name = "Timescale Writer",
        configClazz = TimescaleNodeConfig.class,
        nodeDescription = "Write incoming telemetry to TimescaleDB (PostgreSQL JDBC)",
        nodeDetails = "Accepts { ts, values:{k:v} } or plain {k:v}. Inserts one row per key."
)
public class TimescaleWriterNode implements TbNode {

    private TimescaleNodeConfig cfg;
    private DataSource ds;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration config) throws TbNodeException {
        cfg = TbNodeUtils.convert(config, TimescaleNodeConfig.class);
        try {
            // Build JDBC URL dynamically from simple fields
            String ssl = (cfg.sslMode == null || cfg.sslMode.isBlank()) ? "disable" : cfg.sslMode;
            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                    cfg.host, cfg.port, cfg.db, ssl);

            // Build INSERT SQL once; uses schema + table from config
            cfg.insertSql = String.format(
                    "INSERT INTO %s.%s(ts, device_id, key, val) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING;",
                    cfg.schema, cfg.table
            );

            org.postgresql.ds.PGSimpleDataSource p = new org.postgresql.ds.PGSimpleDataSource();
            p.setURL(jdbcUrl);
            p.setUser(cfg.user);
            p.setPassword(cfg.password);
            this.ds = p;
        } catch (Exception e) {
            throw new TbNodeException(e);
        }
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        try (Connection c = ds.getConnection()) {
            JsonNode root = JacksonUtil.toJsonNode(msg.getData());

            long tsMs = System.currentTimeMillis();
            if (root.has("ts") && root.get("ts").isNumber()) {
                tsMs = root.get("ts").asLong();
                if (String.valueOf(tsMs).length() == 10) tsMs *= 1000L; // seconds -> ms
            }

            JsonNode values = (root.has("values") && root.get("values").isObject())
                    ? root.get("values") : root;

            try (PreparedStatement ps = c.prepareStatement(cfg.insertSql)) {
                String originatorId = msg.getOriginator().getId().toString();

                Iterator<String> it = values.fieldNames();
                while (it.hasNext()) {
                    String key = it.next();
                    JsonNode v = values.get(key);
                    String val = v.isTextual() ? v.asText() : v.toString();

                    ps.setTimestamp(1, new Timestamp(tsMs)); // timestamp/timestamptz
                    ps.setString(2, originatorId);
                    ps.setString(3, key);
                    ps.setString(4, val);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            ctx.tellSuccess(msg);
        } catch (Exception e) {
            ctx.tellFailure(msg, e);
        }
    }

    @Override public void destroy() {}
}
