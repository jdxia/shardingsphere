/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.connection.kernel;

import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.config.props.ConfigurationPropertyKey;
import org.apache.shardingsphere.infra.executor.sql.context.ExecutionContext;
import org.apache.shardingsphere.infra.executor.sql.context.ExecutionContextBuilder;
import org.apache.shardingsphere.infra.executor.sql.log.SQLLogger;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.rule.RuleMetaData;
import org.apache.shardingsphere.infra.rewrite.SQLRewriteEntry;
import org.apache.shardingsphere.infra.rewrite.engine.result.SQLRewriteResult;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.route.engine.SQLRouteEngine;
import org.apache.shardingsphere.infra.session.connection.ConnectionContext;
import org.apache.shardingsphere.infra.session.query.QueryContext;

/**
 * Kernel processor.
 */
public final class KernelProcessor {
    
    /**
     * Generate execution context.
     *
     * @param queryContext query context
     * @param database database
     * @param globalRuleMetaData global rule meta data
     * @param props configuration properties
     * @param connectionContext connection context
     * @return execution context
     */
    public ExecutionContext generateExecutionContext(final QueryContext queryContext, final ShardingSphereDatabase database, final RuleMetaData globalRuleMetaData,
                                                     final ConfigurationProperties props, final ConnectionContext connectionContext) {
        // 创建路由引擎 并执行路由方法, 重点 sql路由的
        RouteContext routeContext = route(queryContext, database, globalRuleMetaData, props, connectionContext);
        // SQL 重写, 重点
        SQLRewriteResult rewriteResult = rewrite(queryContext, database, globalRuleMetaData, props, routeContext, connectionContext);
        // 创建执行上下文
        ExecutionContext result = createExecutionContext(queryContext, database, routeContext, rewriteResult);
        logSQL(queryContext, props, result);
        return result;
    }
    
    private RouteContext route(final QueryContext queryContext, final ShardingSphereDatabase database,
                               final RuleMetaData globalRuleMetaData, final ConfigurationProperties props, final ConnectionContext connectionContext) {
        // 创建路由引擎 并执行路由方法, route方法 重点 sql路由的
        return new SQLRouteEngine(database.getRuleMetaData().getRules(), props).route(connectionContext, queryContext, globalRuleMetaData, database);
    }
    
    private SQLRewriteResult rewrite(final QueryContext queryContext, final ShardingSphereDatabase database, final RuleMetaData globalRuleMetaData,
                                     final ConfigurationProperties props, final RouteContext routeContext, final ConnectionContext connectionContext) {
        // 创建改写器
        SQLRewriteEntry sqlRewriteEntry = new SQLRewriteEntry(database, globalRuleMetaData, props);
        // 改写操作, 重点 sql改写
        return sqlRewriteEntry.rewrite(queryContext, routeContext, connectionContext);
    }
    
    private ExecutionContext createExecutionContext(final QueryContext queryContext, final ShardingSphereDatabase database, final RouteContext routeContext, final SQLRewriteResult rewriteResult) {
        return new ExecutionContext(queryContext, ExecutionContextBuilder.build(database, rewriteResult, queryContext.getSqlStatementContext()), routeContext);
    }
    
    private void logSQL(final QueryContext queryContext, final ConfigurationProperties props, final ExecutionContext executionContext) {
        if (props.<Boolean>getValue(ConfigurationPropertyKey.SQL_SHOW)) {
            SQLLogger.logSQL(queryContext, props.<Boolean>getValue(ConfigurationPropertyKey.SQL_SIMPLE), executionContext);
        }
    }
}
