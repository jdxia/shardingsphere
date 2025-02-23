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

package org.apache.shardingsphere.infra.executor.kernel;

import lombok.Getter;
import org.apache.shardingsphere.infra.annotation.HighFrequencyInvocation;
import org.apache.shardingsphere.infra.exception.generic.UnknownSQLException;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroup;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroupContext;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutorCallback;
import org.apache.shardingsphere.infra.executor.kernel.thread.ExecutorServiceManager;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Executor engine.
 */
@HighFrequencyInvocation
@Getter
public final class ExecutorEngine implements AutoCloseable {
    
    private final ExecutorServiceManager executorServiceManager;
    
    private ExecutorEngine(final int executorSize) {
        executorServiceManager = new ExecutorServiceManager(executorSize);
    }
    
    /**
     * Create executor engine with executor size.
     *
     * @param executorSize executor size
     * @return created executor engine
     */
    public static ExecutorEngine createExecutorEngineWithSize(final int executorSize) {
        return new ExecutorEngine(executorSize);
    }
    
    /**
     * Execute.
     *
     * @param executionGroupContext execution group context
     * @param firstCallback first executor callback
     * @param callback other executor callback
     * @param serial whether using multi thread execute or not
     * @param <I> type of input value
     * @param <O> type of return value
     * @return execute result
     * @throws SQLException throw if execute failure
     */
    public <I, O> List<O> execute(final ExecutionGroupContext<I> executionGroupContext,
                                  final ExecutorCallback<I, O> firstCallback, final ExecutorCallback<I, O> callback, final boolean serial) throws SQLException {
        if (executionGroupContext.getInputGroups().isEmpty()) {
            return Collections.emptyList();
        }
        // parallelExecute  首先将JDBCExecutorCallback放入到线程池，然后执行
        return serial ? serialExecute(executionGroupContext.getInputGroups().iterator(), executionGroupContext.getReportContext().getProcessId(), firstCallback, callback)
                : parallelExecute(executionGroupContext.getInputGroups().iterator(), executionGroupContext.getReportContext().getProcessId(), firstCallback, callback);
    }
    
    private <I, O> List<O> serialExecute(final Iterator<ExecutionGroup<I>> executionGroups, final String processId, final ExecutorCallback<I, O> firstCallback,
                                         final ExecutorCallback<I, O> callback) throws SQLException {
        ExecutionGroup<I> firstInputs = executionGroups.next();
        List<O> result = new LinkedList<>(syncExecute(firstInputs, processId, null == firstCallback ? callback : firstCallback));
        while (executionGroups.hasNext()) {
            result.addAll(syncExecute(executionGroups.next(), processId, callback));
        }
        return result;
    }
    
    private <I, O> List<O> parallelExecute(final Iterator<ExecutionGroup<I>> executionGroups, final String processId, final ExecutorCallback<I, O> firstCallback,
                                           final ExecutorCallback<I, O> callback) throws SQLException {
        ExecutionGroup<I> firstInputs = executionGroups.next();
        // 线城池添加 JDBCExecutorCallback
        Collection<Future<Collection<O>>> restResultFutures = asyncExecute(executionGroups, processId, callback);
        // 异步执行
        return getGroupResults(syncExecute(firstInputs, processId, null == firstCallback ? callback : firstCallback), restResultFutures);
    }
    
    private <I, O> Collection<O> syncExecute(final ExecutionGroup<I> executionGroup, final String processId, final ExecutorCallback<I, O> callback) throws SQLException {
        return callback.execute(executionGroup.getInputs(), true, processId);
    }
    
    private <I, O> Collection<Future<Collection<O>>> asyncExecute(final Iterator<ExecutionGroup<I>> executionGroups, final String processId, final ExecutorCallback<I, O> callback) {
        Collection<Future<Collection<O>>> result = new LinkedList<>();
        while (executionGroups.hasNext()) {
            result.add(asyncExecute(executionGroups.next(), processId, callback));
        }
        return result;
    }
    
    private <I, O> Future<Collection<O>> asyncExecute(final ExecutionGroup<I> executionGroup, final String processId, final ExecutorCallback<I, O> callback) {
        return executorServiceManager.getExecutorService().submit(() -> callback.execute(executionGroup.getInputs(), false, processId));
    }
    
    private <O> List<O> getGroupResults(final Collection<O> firstResults, final Collection<Future<Collection<O>>> restFutures) throws SQLException {
        List<O> result = new LinkedList<>(firstResults);
        for (Future<Collection<O>> each : restFutures) {
            try {
                result.addAll(each.get());
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (final ExecutionException ex) {
                return throwException(ex);
            }
        }
        return result;
    }
    
    private <O> List<O> throwException(final Exception exception) throws SQLException {
        if (exception.getCause() instanceof SQLException) {
            throw (SQLException) exception.getCause();
        }
        throw new UnknownSQLException(exception);
    }
    
    @Override
    public void close() {
        executorServiceManager.close();
    }
}
