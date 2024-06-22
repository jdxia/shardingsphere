package org.apache.shardingsphere.driver.study;

import lombok.Getter;
import org.apache.shardingsphere.infra.binder.context.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.hint.HintManager;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.rule.RuleMetaData;
import org.apache.shardingsphere.infra.metadata.user.Grantee;
import org.apache.shardingsphere.sharding.algorithm.audit.DMLShardingConditionsShardingAuditAlgorithm;
import org.apache.shardingsphere.sharding.spi.ShardingAuditAlgorithm;

import java.util.List;
import java.util.Properties;

public class MyShardingAuditAlgorithm implements ShardingAuditAlgorithm {

    private final String AUDIT_TYPE = "MY_SHARDING_AUDIT_ALGORITHM";

    @Getter
    private Properties props;

    private DMLShardingConditionsShardingAuditAlgorithm dmlShardingConditionsShardingAuditAlgorithm;

    @Override
    public void init(Properties props) {
        this.props = props;

        this.dmlShardingConditionsShardingAuditAlgorithm = new DMLShardingConditionsShardingAuditAlgorithm();
    }

    @Override
    public void check(SQLStatementContext sqlStatementContext, List<Object> params, Grantee grantee, RuleMetaData globalRuleMetaData, ShardingSphereDatabase database) {
        boolean hitShardingCondition = HintManager.isInstantiated() && HintManager.getDataSourceName().isPresent();

        if (!hitShardingCondition) {
            // 没有配置hitManager就走 默认检查
            dmlShardingConditionsShardingAuditAlgorithm.check(sqlStatementContext, params, grantee, globalRuleMetaData, database);
        }
    }

    @Override
    public String getType() {
        return AUDIT_TYPE;
    }
}
