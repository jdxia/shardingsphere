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

package org.apache.shardingsphere.sharding.yaml.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.shardingsphere.infra.algorithm.core.yaml.YamlAlgorithmConfiguration;
import org.apache.shardingsphere.infra.yaml.config.pojo.rule.YamlRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.yaml.config.cache.YamlShardingCacheConfiguration;
import org.apache.shardingsphere.sharding.yaml.config.rule.YamlShardingAutoTableRuleConfiguration;
import org.apache.shardingsphere.sharding.yaml.config.rule.YamlTableRuleConfiguration;
import org.apache.shardingsphere.sharding.yaml.config.strategy.audit.YamlShardingAuditStrategyConfiguration;
import org.apache.shardingsphere.sharding.yaml.config.strategy.keygen.YamlKeyGenerateStrategyConfiguration;
import org.apache.shardingsphere.sharding.yaml.config.strategy.sharding.YamlShardingStrategyConfiguration;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Sharding rule configuration for YAML.
 */
@Getter
@Setter
public final class YamlShardingRuleConfiguration implements YamlRuleConfiguration {
    
    private Map<String, YamlTableRuleConfiguration> tables = new LinkedHashMap<>();
    
    private Map<String, YamlShardingAutoTableRuleConfiguration> autoTables = new LinkedHashMap<>();
    
    private Collection<String> bindingTables = new LinkedList<>();
    
    private YamlShardingStrategyConfiguration defaultDatabaseStrategy;
    
    private YamlShardingStrategyConfiguration defaultTableStrategy;
    
    private YamlKeyGenerateStrategyConfiguration defaultKeyGenerateStrategy;

    // 默认审计策略
    private YamlShardingAuditStrategyConfiguration defaultAuditStrategy;
    
    private Map<String, YamlAlgorithmConfiguration> shardingAlgorithms = new LinkedHashMap<>();
    
    private Map<String, YamlAlgorithmConfiguration> keyGenerators = new LinkedHashMap<>();

    // 审计算法
    private Map<String, YamlAlgorithmConfiguration> auditors = new LinkedHashMap<>();
    
    private String defaultShardingColumn;
    
    private YamlShardingCacheConfiguration shardingCache;
    
    @Override
    public Class<ShardingRuleConfiguration> getRuleConfigurationType() {
        return ShardingRuleConfiguration.class;
    }
}
