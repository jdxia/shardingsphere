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

package org.apache.shardingsphere.sharding.algorithm.sharding.inline;

import com.google.common.base.Strings;
import groovy.lang.MissingMethodException;
import org.apache.shardingsphere.infra.algorithm.core.exception.AlgorithmInitializationException;
import org.apache.shardingsphere.infra.exception.core.ShardingSpherePreconditions;
import org.apache.shardingsphere.infra.exception.generic.UnsupportedSQLOperationException;
import org.apache.shardingsphere.infra.expr.core.InlineExpressionParserFactory;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;
import org.apache.shardingsphere.sharding.exception.algorithm.MismatchedInlineShardingAlgorithmExpressionAndColumnException;
import org.apache.shardingsphere.sharding.exception.data.NullShardingValueException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Inline sharding algorithm.
 */
public final class InlineShardingAlgorithm implements StandardShardingAlgorithm<Comparable<?>> {
    
    private static final String ALGORITHM_EXPRESSION_KEY = "algorithm-expression";
    
    private static final String ALLOW_RANGE_QUERY_KEY = "allow-range-query-with-inline-sharding";
    
    private String algorithmExpression;
    
    private boolean allowRangeQuery;
    
    @Override
    public void init(final Properties props) {
        algorithmExpression = getAlgorithmExpression(props);
        allowRangeQuery = isAllowRangeQuery(props);
    }
    
    private String getAlgorithmExpression(final Properties props) {
        String expression = props.getProperty(ALGORITHM_EXPRESSION_KEY);
        ShardingSpherePreconditions.checkState(!Strings.isNullOrEmpty(expression), () -> new AlgorithmInitializationException(this, "Inline sharding algorithm expression cannot be null or empty"));
        return InlineExpressionParserFactory.newInstance(expression.trim()).handlePlaceHolder();
    }
    
    private boolean isAllowRangeQuery(final Properties props) {
        return Boolean.parseBoolean(props.getOrDefault(ALLOW_RANGE_QUERY_KEY, Boolean.FALSE.toString()).toString());
    }
    
    @Override
    public String doSharding(final Collection<String> availableTargetNames, final PreciseShardingValue<Comparable<?>> shardingValue) {
        // 检查分片键的值是否为空
        ShardingSpherePreconditions.checkNotNull(shardingValue.getValue(), NullShardingValueException::new);
        // 分片键名称：id
        String columnName = shardingValue.getColumnName();
        // 表达式是否包含分片键：ds_order_${id % 2} 是否包含 id
        ShardingSpherePreconditions.checkState(algorithmExpression.contains(columnName), () -> new MismatchedInlineShardingAlgorithmExpressionAndColumnException(algorithmExpression, columnName));
        Map<String, Comparable<?>> map = new LinkedHashMap<>();
        map.put(columnName, shardingValue.getValue());
        try {
            return InlineExpressionParserFactory.newInstance(algorithmExpression).evaluateWithArgs(map);
        } catch (final MissingMethodException ignored) {
            throw new MismatchedInlineShardingAlgorithmExpressionAndColumnException(algorithmExpression, columnName);
        }
    }
    
    @Override
    public Collection<String> doSharding(final Collection<String> availableTargetNames, final RangeShardingValue<Comparable<?>> shardingValue) {
        ShardingSpherePreconditions.checkState(allowRangeQuery,
                () -> new UnsupportedSQLOperationException(String.format("Since the property of `%s` is false, inline sharding algorithm can not tackle with range query", ALLOW_RANGE_QUERY_KEY)));
        return availableTargetNames;
    }
    
    @Override
    public Optional<String> getAlgorithmStructure(final String dataNodePrefix, final String shardingColumn) {
        return Optional.of(algorithmExpression.replaceFirst(dataNodePrefix, "").replaceFirst(shardingColumn, "").replaceAll(" ", ""));
    }
    
    @Override
    public String getType() {
        return "INLINE";
    }
}
