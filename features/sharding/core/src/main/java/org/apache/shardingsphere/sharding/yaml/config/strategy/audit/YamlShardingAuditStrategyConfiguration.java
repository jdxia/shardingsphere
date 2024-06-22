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

package org.apache.shardingsphere.sharding.yaml.config.strategy.audit;

import lombok.Getter;
import lombok.Setter;
import org.apache.shardingsphere.infra.util.yaml.YamlConfiguration;

import java.util.Collection;

/**
 * Sharing audit strategy configuration for YAML.
 */
@Getter
@Setter
public final class YamlShardingAuditStrategyConfiguration implements YamlConfiguration {

    // 多个审计算法名称，支持同时使用多个审计
    private Collection<String> auditorNames;

    // 否允许用户通过 Hint 的形式跳过分片审计，该参数默认是 true，即允许。
    private boolean allowHintDisable = true;
}
