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

package org.apache.shardingsphere.driver.executor;

import org.apache.shardingsphere.driver.executor.callback.ExecuteQueryCallback;
import org.apache.shardingsphere.infra.binder.context.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.connection.refresher.MetaDataRefreshEngine;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroupContext;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.driver.jdbc.JDBCExecutionUnit;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.driver.jdbc.JDBCExecutor;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.driver.jdbc.JDBCExecutorCallback;
import org.apache.shardingsphere.infra.executor.sql.execute.result.query.QueryResult;
import org.apache.shardingsphere.infra.executor.sql.process.ProcessEngine;
import org.apache.shardingsphere.infra.instance.mode.ModeContextManager;
import org.apache.shardingsphere.infra.route.context.RouteUnit;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;
import org.apache.shardingsphere.infra.rule.attribute.datanode.DataNodeRuleAttribute;
import org.apache.shardingsphere.infra.session.query.QueryContext;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.mode.metadata.MetaDataContexts;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Driver JDBC executor.
 */
public final class DriverJDBCExecutor {
    
    private final String databaseName;
    
    private final MetaDataContexts metaDataContexts;
    
    private final ModeContextManager modeContextManager;
    
    private final JDBCExecutor jdbcExecutor;
    
    private final ProcessEngine processEngine = new ProcessEngine();
    
    public DriverJDBCExecutor(final String databaseName, final ContextManager contextManager, final JDBCExecutor jdbcExecutor) {
        this.databaseName = databaseName;
        this.jdbcExecutor = jdbcExecutor;
        metaDataContexts = contextManager.getMetaDataContexts();
        modeContextManager = contextManager.getInstanceContext().getModeContextManager();
    }
    
    /**
     * Execute query.
     *
     * @param executionGroupContext execution group context
     * @param queryContext query context
     * @param callback execute query callback
     * @return query results
     * @throws SQLException SQL exception
     */
    public List<QueryResult> executeQuery(final ExecutionGroupContext<JDBCExecutionUnit> executionGroupContext,
                                          final QueryContext queryContext, final ExecuteQueryCallback callback) throws SQLException {
        try {
            processEngine.executeSQL(executionGroupContext, queryContext);
            return jdbcExecutor.execute(executionGroupContext, callback);
        } finally {
            processEngine.completeSQLExecution(executionGroupContext.getReportContext().getProcessId());
        }
    }
    
    /**
     * Execute update.
     *
     * @param executionGroupContext execution group context
     * @param queryContext query context
     * @param routeUnits route units
     * @param callback JDBC executor callback
     * @return effected records count
     * @throws SQLException SQL exception
     */
    public int executeUpdate(final ExecutionGroupContext<JDBCExecutionUnit> executionGroupContext,
                             final QueryContext queryContext, final Collection<RouteUnit> routeUnits, final JDBCExecutorCallback<Integer> callback) throws SQLException {
        try {
            processEngine.executeSQL(executionGroupContext, queryContext);
            SQLStatementContext sqlStatementContext = queryContext.getSqlStatementContext();
            List<Integer> results = doExecute(executionGroupContext, sqlStatementContext, routeUnits, callback);
            return isNeedAccumulate(metaDataContexts.getMetaData().getDatabase(queryContext.getDatabaseNameFromSQLStatement().orElse(databaseName)).getRuleMetaData().getRules(), sqlStatementContext)
                    ? accumulate(results)
                    : results.get(0);
        } finally {
            processEngine.completeSQLExecution(executionGroupContext.getReportContext().getProcessId());
        }
    }
    
    private boolean isNeedAccumulate(final Collection<ShardingSphereRule> rules, final SQLStatementContext sqlStatementContext) {
        for (ShardingSphereRule each : rules) {
            Optional<DataNodeRuleAttribute> ruleAttribute = each.getAttributes().findAttribute(DataNodeRuleAttribute.class);
            if (ruleAttribute.isPresent() && ruleAttribute.get().isNeedAccumulate(sqlStatementContext.getTablesContext().getTableNames())) {
                return true;
            }
        }
        return false;
    }
    
    private int accumulate(final List<Integer> updateResults) {
        int result = 0;
        for (Integer each : updateResults) {
            result += null == each ? 0 : each;
        }
        return result;
    }
    
    /**
     * Execute SQL.
     *
     * @param executionGroupContext execution group context
     * @param queryContext query context
     * @param routeUnits route units
     * @param callback JDBC executor callback
     * @return return true if is DQL, false if is DML
     * @throws SQLException SQL exception
     */
    public boolean execute(final ExecutionGroupContext<JDBCExecutionUnit> executionGroupContext, final QueryContext queryContext,
                           final Collection<RouteUnit> routeUnits, final JDBCExecutorCallback<Boolean> callback) throws SQLException {
        try {
            // 执行 SQL, executeSQL方法中并没有真正的执行，只是在进程注册器中，添加了一个进程Process
            processEngine.executeSQL(executionGroupContext, queryContext);
            // 执行
            List<Boolean> results = doExecute(executionGroupContext, queryContext.getSqlStatementContext(), routeUnits, callback);
            return null != results && !results.isEmpty() && null != results.get(0) && results.get(0);
        } finally {
            // 完成执行
            processEngine.completeSQLExecution(executionGroupContext.getReportContext().getProcessId());
        }
    }
    
    private <T> List<T> doExecute(final ExecutionGroupContext<JDBCExecutionUnit> executionGroupContext, final SQLStatementContext sqlStatementContext, final Collection<RouteUnit> routeUnits,
                                  final JDBCExecutorCallback<T> callback) throws SQLException {
        // 执行
        List<T> results = jdbcExecutor.execute(executionGroupContext, callback);
        // 刷新元数据
        new MetaDataRefreshEngine(modeContextManager,
                metaDataContexts.getMetaData().getDatabase(sqlStatementContext.getTablesContext().getDatabaseName().orElse(databaseName)), metaDataContexts.getMetaData().getProps())
                        .refresh(sqlStatementContext, routeUnits);
        return results;
    }
}
