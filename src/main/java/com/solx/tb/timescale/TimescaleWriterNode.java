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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.util.Iterator;

@RuleNode(
        type = ComponentType.ACTION,
        name = "Timescale Writer",
        configClazz = TimescaleNodeConfig.class,
        nodeDescription = "Write incoming telemetry to TimescaleDB existing table structure",
        nodeDetails = "Maps telemetry data to existing table columns: meterid, datetime, insertdate, and measurement columns",
        uiResources = {"static/rulenode/timescale-writer-config.js"},
        configDirective = "tbTimescaleWriterConfig"
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

            // Build INSERT SQL for the existing table structure
            // This will insert a single row with all telemetry data as columns
            cfg.insertSql = buildInsertSql();

            org.postgresql.ds.PGSimpleDataSource p = new org.postgresql.ds.PGSimpleDataSource();
            p.setURL(jdbcUrl);
            p.setUser(cfg.user);
            p.setPassword(cfg.password);
            this.ds = p;
        } catch (Exception e) {
            throw new TbNodeException(e);
        }
    }

    private String buildInsertSql() {
        // Build SQL for inserting into your existing table structure
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(cfg.schema).append(".").append(cfg.table).append(" (");
        sql.append("meterid, datetime, insertdate");

        // Add all the measurement columns
        String[] columns = {
                "i_avg", "i_a", "i_b", "i_c", "i_un_w", "i_un_a", "i_un_b", "i_un_c",
                "v_l_l_avg", "v_a_b", "v_b_c", "v_c_a", "v_l_n_avg", "v_a", "v_b", "v_c",
                "v_un_l_l_w", "v_un_a_b", "v_un_b_c", "v_un_c_a", "v_un_l_n_w", "v_un_a", "v_un_b", "v_un_c",
                "kw_t", "kw_a", "kw_b", "kw_c", "kvar_t", "kvar_a", "kvar_b", "kvar_c",
                "kva_t", "kva_a", "kva_b", "kva_c", "pf_t", "pf_a", "pf_b", "pf_c",
                "dpf_t", "dpf_a", "dpf_b", "dpf_c", "f", "wh_r", "wh_d", "varh_r", "varh_d",
                "vah_r", "vah_d", "last_dem", "pres_dem", "thd_i_a", "thd_i_b", "thd_i_c",
                "thd_i_n", "thd_i_g", "tdd", "thd_v_l_l", "thd_v_a_b", "thd_v_b_c",
                "thd_v_c_a", "thd_v_l_n", "thd_v_a", "thd_v_b", "thd_v_c"
        };

        for (String col : columns) {
            sql.append(", ").append(col);
        }

        sql.append(") VALUES (?, ?, ?");
        for (int i = 0; i < columns.length; i++) {
            sql.append(", ?");
        }
        sql.append(") ON CONFLICT DO NOTHING");

        return sql.toString();
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
                // String originatorId = msg.getOriginator().getId().toString();
                String originatorId = msg.getMetaData().getValue("deviceName");
                if (originatorId == null || originatorId.isBlank()) {
                    originatorId = msg.getOriginator().getId().toString();
                }

                Timestamp timestamp = new Timestamp(tsMs);

                // Set the basic fields
                ps.setString(1, originatorId);  // meterid
                ps.setTimestamp(2, timestamp);  // datetime
                ps.setTimestamp(3, timestamp);  // insertdate

                // Set all measurement columns
                String[] columns = {
                        "i_avg", "i_a", "i_b", "i_c", "i_un_w", "i_un_a", "i_un_b", "i_un_c",
                        "v_l_l_avg", "v_a_b", "v_b_c", "v_c_a", "v_l_n_avg", "v_a", "v_b", "v_c",
                        "v_un_l_l_w", "v_un_a_b", "v_un_b_c", "v_un_c_a", "v_un_l_n_w", "v_un_a", "v_un_b", "v_un_c",
                        "kw_t", "kw_a", "kw_b", "kw_c", "kvar_t", "kvar_a", "kvar_b", "kvar_c",
                        "kva_t", "kva_a", "kva_b", "kva_c", "pf_t", "pf_a", "pf_b", "pf_c",
                        "dpf_t", "dpf_a", "dpf_b", "dpf_c", "f", "wh_r", "wh_d", "varh_r", "varh_d",
                        "vah_r", "vah_d", "last_dem", "pres_dem", "thd_i_a", "thd_i_b", "thd_i_c",
                        "thd_i_n", "thd_i_g", "tdd", "thd_v_l_l", "thd_v_a_b", "thd_v_b_c",
                        "thd_v_c_a", "thd_v_l_n", "thd_v_a", "thd_v_b", "thd_v_c"
                };

                boolean withValue = false;
                for (int i = 0; i < columns.length; i++) {
                    String columnName = columns[i];
                    if (values.has(columnName) && values.get(columnName).isNumber()) {
                        ps.setBigDecimal(i + 4, new BigDecimal(values.get(columnName).asText()));
                        withValue = true;
                    } else {
                        ps.setBigDecimal(i + 4, null);  // Set to NULL if value not present
                    }
                }

                if (withValue) {
                    ps.executeUpdate();
                }
            }

            ctx.tellSuccess(msg);
        } catch (Exception e) {
            ctx.tellFailure(msg, e);
        }
    }

    @Override
    public void destroy() {}
}