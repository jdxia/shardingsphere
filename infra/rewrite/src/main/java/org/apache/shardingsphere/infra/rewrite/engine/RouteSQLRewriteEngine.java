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

package org.apache.shardingsphere.infra.rewrite.engine;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.binder.context.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.binder.context.statement.dml.SelectStatementContext;
import org.apache.shardingsphere.infra.database.core.type.DatabaseType;
import org.apache.shardingsphere.infra.datanode.DataNode;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.resource.unit.StorageUnit;
import org.apache.shardingsphere.infra.metadata.database.rule.RuleMetaData;
import org.apache.shardingsphere.infra.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.infra.rewrite.engine.result.RouteSQLRewriteResult;
import org.apache.shardingsphere.infra.rewrite.engine.result.SQLRewriteUnit;
import org.apache.shardingsphere.infra.rewrite.parameter.builder.ParameterBuilder;
import org.apache.shardingsphere.infra.rewrite.parameter.builder.impl.GroupedParameterBuilder;
import org.apache.shardingsphere.infra.rewrite.parameter.builder.impl.StandardParameterBuilder;
import org.apache.shardingsphere.infra.rewrite.sql.impl.RouteSQLBuilder;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.route.context.RouteUnit;
import org.apache.shardingsphere.infra.session.query.QueryContext;
import org.apache.shardingsphere.sql.parser.sql.common.util.SQLUtils;
import org.apache.shardingsphere.sql.parser.sql.dialect.handler.dml.SelectStatementHandler;
import org.apache.shardingsphere.sqltranslator.rule.SQLTranslatorRule;
import org.apache.shardingsphere.sqltranslator.context.SQLTranslatorContext;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Route SQL rewrite engine.
 */
@RequiredArgsConstructor
public final class RouteSQLRewriteEngine {
    
    private final SQLTranslatorRule translatorRule;
    
    private final ShardingSphereDatabase database;
    
    private final RuleMetaData globalRuleMetaData;

    //会对路由单元按照数据源进行分组, 接着循环所有分组后的路由单元，并创建改写单元
    /**
     * Rewrite SQL and parameters.
     *
     * @param sqlRewriteContext SQL rewrite context
     * @param routeContext route context
     * @param queryContext query context
     * @return SQL rewrite result
     */
    public RouteSQLRewriteResult rewrite(final SQLRewriteContext sqlRewriteContext, final RouteContext routeContext, final QueryContext queryContext) {
        Map<RouteUnit, SQLRewriteUnit> sqlRewriteUnits = new LinkedHashMap<>(routeContext.getRouteUnits().size(), 1F);
        for (Entry<String, Collection<RouteUnit>> entry : aggregateRouteUnitGroups(routeContext.getRouteUnits()).entrySet()) {
            Collection<RouteUnit> routeUnits = entry.getValue();
            if (isNeedAggregateRewrite(sqlRewriteContext.getSqlStatementContext(), routeUnits)) {
                // createSQLRewriteUnit 重点, 执行改写逻辑
                sqlRewriteUnits.put(routeUnits.iterator().next(), createSQLRewriteUnit(sqlRewriteContext, routeContext, routeUnits));
            } else {
                addSQLRewriteUnits(sqlRewriteUnits, sqlRewriteContext, routeContext, routeUnits);
            }
        }
        return new RouteSQLRewriteResult(translate(queryContext, sqlRewriteUnits));
    }
    
    private SQLRewriteUnit createSQLRewriteUnit(final SQLRewriteContext sqlRewriteContext, final RouteContext routeContext, final Collection<RouteUnit> routeUnits) {
        // 所有的SQL
        Collection<String> sql = new LinkedList<>();
        // 参数
        List<Object> params = new LinkedList<>();
        // 事都是查询操作，并且包含 $ 符号
        boolean containsDollarMarker = sqlRewriteContext.getSqlStatementContext() instanceof SelectStatementContext
                && ((SelectStatementContext) (sqlRewriteContext.getSqlStatementContext())).isContainsDollarParameterMarker();
        // 循环当前路由分组中的所有路由
        for (RouteUnit each : routeUnits) {
            // 改写处理
            // 循环当前路由分组中的所有路由时，会创建 RouteSQLBuilder 并执行 toSQL 方法, 将逻辑SQL中的表名改写为当前路由中的真实表名：
            // toSQL看下
            sql.add(SQLUtils.trimSemicolon(new RouteSQLBuilder(sqlRewriteContext, each).toSQL()));
            if (containsDollarMarker && !params.isEmpty()) {
                continue;
            }
            // 添加参数
            params.addAll(getParameters(sqlRewriteContext.getParameterBuilder(), routeContext, each));
        }
        // 使用 UNION ALL 连接所有SQL, 生成了真实改写单元，包含了可执行的真实SQL
        return new SQLRewriteUnit(String.join(" UNION ALL ", sql), params);
    }
    
    private void addSQLRewriteUnits(final Map<RouteUnit, SQLRewriteUnit> sqlRewriteUnits, final SQLRewriteContext sqlRewriteContext,
                                    final RouteContext routeContext, final Collection<RouteUnit> routeUnits) {
        for (RouteUnit each : routeUnits) {
            sqlRewriteUnits.put(each, new SQLRewriteUnit(new RouteSQLBuilder(sqlRewriteContext, each).toSQL(), getParameters(sqlRewriteContext.getParameterBuilder(), routeContext, each)));
        }
    }
    
    private boolean isNeedAggregateRewrite(final SQLStatementContext sqlStatementContext, final Collection<RouteUnit> routeUnits) {
        if (!(sqlStatementContext instanceof SelectStatementContext) || routeUnits.size() == 1) {
            return false;
        }
        SelectStatementContext statementContext = (SelectStatementContext) sqlStatementContext;
        boolean containsSubqueryJoinQuery = statementContext.isContainsSubquery() || statementContext.isContainsJoinQuery();
        boolean containsOrderByLimitClause = !statementContext.getOrderByContext().getItems().isEmpty() || statementContext.getPaginationContext().isHasPagination();
        boolean containsLockClause = SelectStatementHandler.getLockSegment(statementContext.getSqlStatement()).isPresent();
        boolean needAggregateRewrite = !containsSubqueryJoinQuery && !containsOrderByLimitClause && !containsLockClause;
        statementContext.setNeedAggregateRewrite(needAggregateRewrite);
        return needAggregateRewrite;
    }
    
    private Map<String, Collection<RouteUnit>> aggregateRouteUnitGroups(final Collection<RouteUnit> routeUnits) {
        Map<String, Collection<RouteUnit>> result = new LinkedHashMap<>(routeUnits.size(), 1F);
        for (RouteUnit each : routeUnits) {
            String dataSourceName = each.getDataSourceMapper().getActualName();
            result.computeIfAbsent(dataSourceName, unused -> new LinkedList<>()).add(each);
        }
        return result;
    }
    
    private List<Object> getParameters(final ParameterBuilder paramBuilder, final RouteContext routeContext, final RouteUnit routeUnit) {
        if (paramBuilder instanceof StandardParameterBuilder) {
            return paramBuilder.getParameters();
        }
        return routeContext.getOriginalDataNodes().isEmpty()
                ? ((GroupedParameterBuilder) paramBuilder).getParameters()
                : buildRouteParameters((GroupedParameterBuilder) paramBuilder, routeContext, routeUnit);
    }
    
    private List<Object> buildRouteParameters(final GroupedParameterBuilder paramBuilder, final RouteContext routeContext, final RouteUnit routeUnit) {
        List<Object> result = new LinkedList<>();
        int count = 0;
        for (Collection<DataNode> each : routeContext.getOriginalDataNodes()) {
            if (isInSameDataNode(each, routeUnit)) {
                result.addAll(paramBuilder.getParameters(count));
            }
            count++;
        }
        result.addAll(paramBuilder.getGenericParameterBuilder().getParameters());
        return result;
    }
    
    private boolean isInSameDataNode(final Collection<DataNode> dataNodes, final RouteUnit routeUnit) {
        if (dataNodes.isEmpty()) {
            return true;
        }
        for (DataNode each : dataNodes) {
            if (routeUnit.findTableMapper(each.getDataSourceName(), each.getTableName()).isPresent()) {
                return true;
            }
        }
        return false;
    }
    
    private Map<RouteUnit, SQLRewriteUnit> translate(final QueryContext queryContext, final Map<RouteUnit, SQLRewriteUnit> sqlRewriteUnits) {
        Map<RouteUnit, SQLRewriteUnit> result = new LinkedHashMap<>(sqlRewriteUnits.size(), 1F);
        Map<String, StorageUnit> storageUnits = database.getResourceMetaData().getStorageUnits();
        for (Entry<RouteUnit, SQLRewriteUnit> entry : sqlRewriteUnits.entrySet()) {
            DatabaseType storageType = storageUnits.get(entry.getKey().getDataSourceMapper().getActualName()).getStorageType();
            SQLTranslatorContext sqlTranslatorContext = translatorRule.translate(entry.getValue().getSql(), entry.getValue().getParameters(), queryContext, storageType, database, globalRuleMetaData);
            SQLRewriteUnit sqlRewriteUnit = new SQLRewriteUnit(sqlTranslatorContext.getSql(), sqlTranslatorContext.getParameters());
            result.put(entry.getKey(), sqlRewriteUnit);
        }
        return result;
    }
}
