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

package org.apache.shardingsphere.sharding.route.engine;

import org.apache.shardingsphere.infra.annotation.HighFrequencyInvocation;
import org.apache.shardingsphere.infra.binder.context.type.CursorAvailable;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.rule.RuleMetaData;
import org.apache.shardingsphere.infra.route.SQLRouter;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.session.connection.ConnectionContext;
import org.apache.shardingsphere.infra.session.query.QueryContext;
import org.apache.shardingsphere.sharding.cache.route.CachedShardingSQLRouter;
import org.apache.shardingsphere.sharding.constant.ShardingOrder;
import org.apache.shardingsphere.sharding.route.engine.condition.ShardingCondition;
import org.apache.shardingsphere.sharding.route.engine.condition.ShardingConditions;
import org.apache.shardingsphere.sharding.route.engine.condition.engine.ShardingConditionEngine;
import org.apache.shardingsphere.sharding.route.engine.type.ShardingRouteEngineFactory;
import org.apache.shardingsphere.sharding.route.engine.validator.ShardingStatementValidator;
import org.apache.shardingsphere.sharding.route.engine.validator.ShardingStatementValidatorFactory;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dml.DMLStatement;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Sharding SQL router.
 */
@HighFrequencyInvocation
public final class ShardingSQLRouter implements SQLRouter<ShardingRule> {
    
    @Override
    public RouteContext createRouteContext(final QueryContext queryContext, final RuleMetaData globalRuleMetaData, final ShardingSphereDatabase database, final ShardingRule rule,
                                           final ConfigurationProperties props, final ConnectionContext connectionContext) {
        if (rule.isShardingCacheEnabled()) {
            // createRouteContext0 重点是获取路由条件，并执行路由
            Optional<RouteContext> result = new CachedShardingSQLRouter()
                    .loadRouteContext(this::createRouteContext0, queryContext, globalRuleMetaData, database, rule.getShardingCache(), props, connectionContext);
            if (result.isPresent()) {
                return result.get();
            }
        }
        return createRouteContext0(queryContext, globalRuleMetaData, database, rule, props, connectionContext);
    }
    
    private RouteContext createRouteContext0(final QueryContext queryContext, final RuleMetaData globalRuleMetaData, final ShardingSphereDatabase database, final ShardingRule rule,
                                             final ConfigurationProperties props, final ConnectionContext connectionContext) {
        // 获取SQL解析的结果
        SQLStatement sqlStatement = queryContext.getSqlStatementContext().getSqlStatement();
        // 获取路由条件, createShardingConditions 重点
        ShardingConditions shardingConditions = createShardingConditions(queryContext, globalRuleMetaData, database, rule);
        // 创建校验器
        Optional<ShardingStatementValidator> validator = ShardingStatementValidatorFactory.newInstance(sqlStatement, shardingConditions, globalRuleMetaData);
        // 前置校验
        validator.ifPresent(optional -> optional.preValidate(rule, queryContext.getSqlStatementContext(), queryContext.getParameters(), database, props));
        if (sqlStatement instanceof DMLStatement && shardingConditions.isNeedMerge()) {
            shardingConditions.merge();
        }
        // 执行路由，返回结果, newInstance 重点 sql路由的
        RouteContext result = ShardingRouteEngineFactory.newInstance(rule, database, queryContext, shardingConditions, props, connectionContext, globalRuleMetaData).route(rule);
        // 后置校验
        validator.ifPresent(optional -> optional.postValidate(rule, queryContext.getSqlStatementContext(), queryContext.getHintValueContext(), queryContext.getParameters(), database, props, result));
        return result;
    }
    
    private ShardingConditions createShardingConditions(final QueryContext queryContext,
                                                        final RuleMetaData globalRuleMetaData, final ShardingSphereDatabase database, final ShardingRule rule) {
        List<ShardingCondition> shardingConditions;
        // 不是 DML 也是不 CURSOR 操作，这直接返回空
        if (queryContext.getSqlStatementContext().getSqlStatement() instanceof DMLStatement || queryContext.getSqlStatementContext() instanceof CursorAvailable) {
            shardingConditions = new ShardingConditionEngine(globalRuleMetaData, database, rule).createShardingConditions(queryContext.getSqlStatementContext(), queryContext.getParameters());
        } else {
            shardingConditions = Collections.emptyList();
        }
        // 返回, 回到开始地方
        return new ShardingConditions(shardingConditions, queryContext.getSqlStatementContext(), rule);
    }
    
    @Override
    public void decorateRouteContext(final RouteContext routeContext, final QueryContext queryContext, final ShardingSphereDatabase database, final ShardingRule rule,
                                     final ConfigurationProperties props, final ConnectionContext connectionContext) {
        // TODO
    }
    
    @Override
    public int getOrder() {
        return ShardingOrder.ORDER;
    }
    
    @Override
    public Class<ShardingRule> getTypeClass() {
        return ShardingRule.class;
    }
}
