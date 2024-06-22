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

package org.apache.shardingsphere.infra.route.engine.impl;

import org.apache.shardingsphere.infra.annotation.HighFrequencyInvocation;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.hint.HintManager;
import org.apache.shardingsphere.infra.hint.HintValueContext;
import org.apache.shardingsphere.infra.exception.kernel.syntax.hint.DataSourceHintNotExistsException;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.resource.unit.StorageUnit;
import org.apache.shardingsphere.infra.metadata.database.rule.RuleMetaData;
import org.apache.shardingsphere.infra.route.SQLRouter;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.route.context.RouteMapper;
import org.apache.shardingsphere.infra.route.context.RouteUnit;
import org.apache.shardingsphere.infra.route.engine.SQLRouteExecutor;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;
import org.apache.shardingsphere.infra.session.connection.ConnectionContext;
import org.apache.shardingsphere.infra.session.query.QueryContext;
import org.apache.shardingsphere.infra.spi.type.ordered.OrderedSPILoader;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Partial SQL route executor.
 */
@HighFrequencyInvocation
public final class PartialSQLRouteExecutor implements SQLRouteExecutor {
    
    private final ConfigurationProperties props;

    @SuppressWarnings("rawtypes")
    private final Map<ShardingSphereRule, SQLRouter> routers;
    
    public PartialSQLRouteExecutor(final Collection<ShardingSphereRule> rules, final ConfigurationProperties props) {
        this.props = props;
        routers = OrderedSPILoader.getServices(SQLRouter.class, rules);
    }
    
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RouteContext route(final ConnectionContext connectionContext, final QueryContext queryContext, final RuleMetaData globalRuleMetaData, final ShardingSphereDatabase database) {
        RouteContext result = new RouteContext();
        // 查询 HINT 配置的数据源
        Optional<String> dataSourceName = findDataSourceByHint(queryContext.getHintValueContext(), database.getResourceMetaData().getStorageUnits());
        if (dataSourceName.isPresent()) {
            // 存在 HINT 添加路由执行单元
            result.getRouteUnits().add(new RouteUnit(new RouteMapper(dataSourceName.get(), dataSourceName.get()), Collections.emptyList()));
            return result;
        }
        // 循环 路由器
        for (Entry<ShardingSphereRule, SQLRouter> entry : routers.entrySet()) {
            if (result.getRouteUnits().isEmpty()) {
                // 创建路由上下文
                // createRouteContext 重点 sql 路由的, org.apache.shardingsphere.sharding.route.engine.ShardingSQLRouter.createRouteContext
                result = entry.getValue().createRouteContext(queryContext, globalRuleMetaData, database, entry.getKey(), props, connectionContext);
            } else {
                entry.getValue().decorateRouteContext(result, queryContext, database, entry.getKey(), props, connectionContext);
            }
        }
        // 没有找到路由处理
        if (result.getRouteUnits().isEmpty() && 1 == database.getResourceMetaData().getStorageUnits().size()) {
            String singleDataSourceName = database.getResourceMetaData().getStorageUnits().keySet().iterator().next();
            result.getRouteUnits().add(new RouteUnit(new RouteMapper(singleDataSourceName, singleDataSourceName), Collections.emptyList()));
        }
        return result;
    }
    
    private Optional<String> findDataSourceByHint(final HintValueContext hintValueContext, final Map<String, StorageUnit> storageUnits) {
        Optional<String> result = HintManager.isInstantiated() && HintManager.getDataSourceName().isPresent() ? HintManager.getDataSourceName() : hintValueContext.findHintDataSourceName();
        if (result.isPresent() && !storageUnits.containsKey(result.get())) {
            throw new DataSourceHintNotExistsException(result.get());
        }
        return result;
    }
}
