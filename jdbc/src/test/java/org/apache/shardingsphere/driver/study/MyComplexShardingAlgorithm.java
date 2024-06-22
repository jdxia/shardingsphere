package org.apache.shardingsphere.driver.study;

import org.apache.shardingsphere.infra.algorithm.core.exception.AlgorithmInitializationException;
import org.apache.shardingsphere.infra.binder.context.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.exception.core.ShardingSpherePreconditions;
import org.apache.shardingsphere.infra.exception.generic.UnsupportedSQLOperationException;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.rule.RuleMetaData;
import org.apache.shardingsphere.infra.metadata.user.Grantee;
import org.apache.shardingsphere.infra.spi.annotation.SingletonSPI;
import org.apache.shardingsphere.sharding.algorithm.sharding.ShardingAutoTableAlgorithmUtils;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;
import org.apache.shardingsphere.sharding.exception.algorithm.MismatchedComplexInlineShardingAlgorithmColumnAndValueSizeException;
import org.apache.shardingsphere.sharding.exception.data.NullShardingValueException;
import org.apache.shardingsphere.sharding.spi.ShardingAuditAlgorithm;
import org.junit.platform.commons.util.CollectionUtils;

import java.util.*;

@SingletonSPI
public class MyComplexShardingAlgorithm implements ComplexKeysShardingAlgorithm<Comparable<?>> {

    private static final String ALLOW_RANGE_QUERY_KEY = "allow-range-query-with-inline-sharding";

    private boolean allowRangeQuery;

    private String dbName;

    @Override
    public Collection<String> doSharding(Collection availableTargetNames, ComplexKeysShardingValue shardingValue) {
        // 1. 检查分片键值
        boolean shardingValueExists = Objects.isNull(shardingValue.getColumnNameAndRangeValuesMap()) || shardingValue.getColumnNameAndRangeValuesMap().isEmpty();
        ShardingSpherePreconditions.checkState(shardingValueExists, () -> new UnsupportedSQLOperationException("没有找到分区键的值, 会进行全库路由, 同时目前不支持分区键的范围查找"));

//        ShardingSpherePreconditions.checkState(this.allowRangeQuery, () -> new UnsupportedSQLOperationException(String.format("Since the property of `%s` is false, inline sharding algorithm can not tackle with range query", "allow-range-query-with-inline-sharding")));

        // 2. 获取分片键、值
        Map<String, Collection<Comparable<?>>> columnNameAndShardingValuesMap = shardingValue.getColumnNameAndShardingValuesMap(); //user_id 对应的值

        // 这是范围的先不处理
        Map columnNameAndRangeValuesMap = shardingValue.getColumnNameAndRangeValuesMap();

        Set<String> resultSet = new HashSet<>();

        int dbCount = availableTargetNames.size();

        columnNameAndShardingValuesMap.keySet().stream().forEach(key -> {
            Collection<Comparable<?>> shardingValues = columnNameAndShardingValuesMap.get(key);
            if (Objects.nonNull(shardingValues) && !shardingValues.isEmpty()) {
                // 3. 获取分片键值, 可能是map, 可能是list
                shardingValues.stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .filter(x -> !Objects.equals("0", x))  // 一些数据库默认值可能是0
                        .forEach(str -> {
                            // 后四位截取
//                            String lastFourDigits = str.length() > 4 ? str.substring(str.length() - 4) : str;

//                            int resNumber = Integer.parseInt(lastFourDigits);

                            // Step 3: 进行 取模操作
                            String resultDB = (Integer.parseInt(str) % dbCount) + "";

                            resultSet.add(dbName + resultDB);

//                            ShardingAutoTableAlgorithmUtils.findMatchedTargetName(availableTargetNames, suffix, shardingValue.getDataNodeInfo()).orElse(null);

                        });
            }
        });

        return resultSet;
    }

    @Override
    public void init(Properties props) {
        ComplexKeysShardingAlgorithm.super.init(props);
        this.dbName = this.getAlgorithmDBName(props);
//        ShardingSpherePreconditions.checkState(result > 0, () -> new AlgorithmInitializationException(this, "Start offset can not be less than 0."));
        this.allowRangeQuery = this.isAllowRangeQuery(props);
    }

    private String getAlgorithmDBName(Properties props) {
        return props.getProperty("DB_NAME", "db");
    }


    private boolean isAllowRangeQuery(Properties props) {
        return Boolean.parseBoolean(props.getOrDefault(ALLOW_RANGE_QUERY_KEY, Boolean.FALSE.toString()).toString());
    }

//    @Override
//    public String getType() {
//        return "MY_COMPLEX_SHARDING";
//    }

}
