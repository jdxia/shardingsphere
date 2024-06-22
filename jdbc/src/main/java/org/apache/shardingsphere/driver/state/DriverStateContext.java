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

package org.apache.shardingsphere.driver.state;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader;
import org.apache.shardingsphere.mode.manager.ContextManager;

import java.sql.Connection;

/**
 * Driver state context.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DriverStateContext {

    /**
     * Get connection.
     *
     * @param databaseName database name
     * @param contextManager context manager
     * @return connection
     */
    public static Connection getConnection(final String databaseName, final ContextManager contextManager) {
        /**
         * DriverState 目前在 JDBC 驱动中一共有 3 个实现，它们对应着 ShardingSphere 计算节点（Instance）处于不同运行状态时，JDBC Driver 在获取新连接时应采用的策略。
         * OKDriverState
         * • 正常运行状态（InstanceState.OK）。
         * • getConnection() 返回真正的 ShardingSphereConnection，一切功能正常可用。
         * CircuitBreakDriverState
         * • 当计算节点被置为熔断 / 故障转移状态（InstanceState.CIRCUIT_BREAK）时触发。
         * • getConnection() 返回 CircuitBreakerDataSource#getConnection()，最终拿到的是 CircuitBreakerConnection。
         * • 这个连接里的 Statement、PrepareStatement 等都会直接返回空实现或抛出不支持异常，保证：
         * – 客户端能拿到一个“合法的” java.sql.Connection 对象，不至于 NullPointerException；
         * – 任何真正的数据库操作都会被快速拒绝或 NO‑OP，提示实例已熔断。
         * • 典型场景：后台检测到该节点数据源不可用、规则加载失败等，需要对外“熔断”而不是继续服务。
         * LockDriverState
         * • 当集群被管理员显式加锁（InstanceState.LOCK / ClusterState.LOCK）时触发。
         * • 当前实现中 getConnection() 直接抛出 UnsupportedSQLOperationException("LockDriverState")。
         * • 目的：集群维护窗口、数据迁移等场景，需要暂时禁止新连接或 SQL 执行。
         * 触发流程回顾
         * a) 其他模块（治理中心事件、管理命令等）调用 InstanceStateContext#switchToValidState / switchToInvalidState 改变 currentState。
         * b) DriverStateContext.getConnection() 每次新连接时读取当前 InstanceState，并通过 TypedSPILoader 按名字加载对应的 DriverState 实现。
         * c) 不同状态下返回不同类型的 Connection，从而对上层应用表现出“正常 / 熔断 / 加锁”的差异行为
         *
         * 默认是 OKDriverState 调用 getConnection
         */
        return TypedSPILoader.getService(
                DriverState.class, contextManager.getInstanceContext().getInstance().getState().getCurrentState().name()).getConnection(databaseName, contextManager);
    }
}
