package com.solx.tb.timescale;

import org.thingsboard.rule.engine.api.NodeConfiguration;

public class TimescaleNodeConfig implements NodeConfiguration<TimescaleNodeConfig> {
    public String jdbcUrl;
    public String user;
    public String password;
    public String insertSql;

    @Override
    public TimescaleNodeConfig defaultConfiguration() {
        TimescaleNodeConfig c = new TimescaleNodeConfig();
        c.jdbcUrl = "jdbc:postgresql://localhost:5432/iot?sslmode=disable";
        c.user = "postgres";
        c.password = "postgres";
        c.insertSql = "INSERT INTO telemetry(ts, device_id, key, val) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING;";
        return c;
    }
}
