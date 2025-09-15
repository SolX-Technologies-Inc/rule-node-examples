/*
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
/* global thingsboardRuleNodeModule */
thingsboardRuleNodeModule.directive('tbTimescaleWriterConfig', function () {
    return {
        restrict: 'E',
        scope: true,
        templateUrl: 'static/rulenode/timescale-writer-config.tpl.html',
        link: function (scope) {
            if (!scope.configuration) scope.configuration = {};
            var cfg = scope.configuration;

            if (!cfg.host)   cfg.host = 'localhost';
            if (!cfg.port)   cfg.port = 5432;
            if (!cfg.db)     cfg.db = 'iot';
            if (!cfg.schema) cfg.schema = 'public';
            if (!cfg.table)  cfg.table = 'telemetry';
            if (!cfg.user)   cfg.user = 'postgres';
            if (!cfg.sslMode) cfg.sslMode = 'disable';

            // internal; node builds SQL in init()
            if ('insertSql' in cfg) delete cfg.insertSql;
        }
    };
});
