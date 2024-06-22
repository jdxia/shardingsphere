package org.apache.shardingsphere.driver.study;


import org.apache.shardingsphere.infra.exception.core.ShardingSpherePreconditions;
import org.apache.shardingsphere.infra.exception.generic.UnsupportedSQLOperationException;
import org.apache.shardingsphere.sharding.algorithm.sharding.ShardingAutoTableAlgorithmUtils;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;
import org.apache.shardingsphere.sharding.exception.data.NullShardingValueException;

import java.util.*;

public class MyShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    private static final String NUMBER = "number";

    private static final String ALLOW_RANGE_QUERY_KEY = "allow-range-query-with-inline-sharding";
    private Integer number;

    private boolean allowRangeQuery;

    public void init(Properties props) {
        this.number = this.getAlgorithmNumber(props);
        this.allowRangeQuery = this.isAllowRangeQuery(props);
    }

    private Integer getAlgorithmNumber(Properties props) {
        return Integer.parseInt(String.valueOf(props.getProperty(NUMBER, "0")));
    }

    private boolean isAllowRangeQuery(Properties props) {
        return Boolean.parseBoolean(props.getOrDefault(ALLOW_RANGE_QUERY_KEY, Boolean.FALSE.toString()).toString());
    }

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> shardingValue) {
        // 1. 检查分片键值
        ShardingSpherePreconditions.checkNotNull(shardingValue.getValue(), NullShardingValueException::new);
        // 2. 获取分片键、值
        String key = shardingValue.getColumnName();

        Long value = shardingValue.getValue();
        // 3. 分片键值 + number
        value += number;
        // 4. 哈希
        long abs = Math.abs(value.hashCode());
        // 5. 取模
        String suffix = String.valueOf(abs % 2);
        return ShardingAutoTableAlgorithmUtils.findMatchedTargetName(availableTargetNames, suffix, shardingValue.getDataNodeInfo()).orElse(null);
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Long> shardingValue) {
        ShardingSpherePreconditions.checkState(this.allowRangeQuery, () -> new UnsupportedSQLOperationException(
            String.format("Since the property of `%s` is false,sharding algorithm can not tackle with range query",
                "allow-range-query-with-inline-sharding")));
        return availableTargetNames;
    }
}
