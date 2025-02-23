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

package org.apache.shardingsphere.infra.executor.sql.prepare;

import com.google.common.collect.Lists;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroup;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroupContext;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroupReportContext;
import org.apache.shardingsphere.infra.executor.sql.context.ExecutionUnit;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.ConnectionMode;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;
import org.apache.shardingsphere.infra.spi.type.ordered.OrderedSPILoader;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Abstract execution prepare engine.
 * 
 * @param <T> type of input value
 */
public abstract class AbstractExecutionPrepareEngine<T> implements ExecutionPrepareEngine<T> {
    
    private final int maxConnectionsSizePerQuery;
    
    @SuppressWarnings("rawtypes")
    private final Map<ShardingSphereRule, ExecutionPrepareDecorator> decorators;
    
    protected AbstractExecutionPrepareEngine(final int maxConnectionsSizePerQuery, final Collection<ShardingSphereRule> rules) {
        this.maxConnectionsSizePerQuery = maxConnectionsSizePerQuery;
        decorators = OrderedSPILoader.getServices(ExecutionPrepareDecorator.class, rules);
    }
    
    @Override
    public final ExecutionGroupContext<T> prepare(final RouteContext routeContext, final Collection<ExecutionUnit> executionUnits,
                                                  final ExecutionGroupReportContext reportContext) throws SQLException {
        return prepare(routeContext, Collections.emptyMap(), executionUnits, reportContext);
    }
    
    @Override
    public final ExecutionGroupContext<T> prepare(final RouteContext routeContext, final Map<String, Integer> connectionOffsets, final Collection<ExecutionUnit> executionUnits,
                                                  final ExecutionGroupReportContext reportContext) throws SQLException {
        Collection<ExecutionGroup<T>> result = new LinkedList<>();
        // 结果集分组
        // 循环所有执行单元
        for (Entry<String, List<ExecutionUnit>> entry : aggregateExecutionUnitGroups(executionUnits).entrySet()) {
            // 数据源
            String dataSourceName = entry.getKey();
            // 执行单元
            List<List<ExecutionUnit>> executionUnitGroups = group(entry.getValue());
            // 计算连接模式
            ConnectionMode connectionMode = maxConnectionsSizePerQuery < entry.getValue().size() ? ConnectionMode.CONNECTION_STRICTLY : ConnectionMode.MEMORY_STRICTLY;
            // group 进行分组,
            // 重点 group分组方法中，根据模式获取连接，并创建分组, org.apache.shardingsphere.infra.executor.sql.prepare.driver.DriverExecutionPrepareEngine.group
            // 添加分组结果
            result.addAll(group(dataSourceName, connectionOffsets.getOrDefault(dataSourceName, 0), executionUnitGroups, connectionMode));
        }
        // 装饰处理, 回到一开始地方
        return decorate(routeContext, result, reportContext);
    }
    
    private List<List<ExecutionUnit>> group(final List<ExecutionUnit> sqlUnits) {
        int desiredPartitionSize = Math.max(0 == sqlUnits.size() % maxConnectionsSizePerQuery ? sqlUnits.size() / maxConnectionsSizePerQuery : sqlUnits.size() / maxConnectionsSizePerQuery + 1, 1);
        return Lists.partition(sqlUnits, desiredPartitionSize);
    }
    
    protected abstract List<ExecutionGroup<T>> group(String dataSourceName, int connectionOffset, List<List<ExecutionUnit>> executionUnitGroups, ConnectionMode connectionMode) throws SQLException;
    
    private Map<String, List<ExecutionUnit>> aggregateExecutionUnitGroups(final Collection<ExecutionUnit> executionUnits) {
        Map<String, List<ExecutionUnit>> result = new LinkedHashMap<>(executionUnits.size(), 1F);
        for (ExecutionUnit each : executionUnits) {
            result.computeIfAbsent(each.getDataSourceName(), unused -> new LinkedList<>()).add(each);
        }
        return result;
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ExecutionGroupContext<T> decorate(final RouteContext routeContext, final Collection<ExecutionGroup<T>> executionGroups, final ExecutionGroupReportContext reportContext) {
        Collection<ExecutionGroup<T>> result = executionGroups;
        for (Entry<ShardingSphereRule, ExecutionPrepareDecorator> each : decorators.entrySet()) {
            result = each.getValue().decorate(routeContext, each.getKey(), result);
        }
        return new ExecutionGroupContext(result, reportContext);
    }
}
