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

package org.apache.shardingsphere.infra.rewrite.sql.impl;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.infra.rewrite.sql.SQLBuilder;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.SQLToken;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.Substitutable;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.generic.ComposableSQLToken;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.generic.SubstitutableColumnNameToken;
import org.apache.shardingsphere.infra.route.context.RouteUnit;

import java.util.Collections;

/**
 * Abstract SQL builder.
 */
@RequiredArgsConstructor
public abstract class AbstractSQLBuilder implements SQLBuilder {
    
    private final SQLRewriteContext context;
    
    private final RouteUnit routeUnit;
    
    @Override
    public final String toSQL() {
        // Token 是否为空
        if (context.getSqlTokens().isEmpty()) {
            return context.getSql();
        }
        Collections.sort(context.getSqlTokens());
        StringBuilder result = new StringBuilder();
        // 生成不包含 Token 的SQL
        // SELECT  id,customer_id,product_id,product_name,product_price,quantity,total_price,order_time,delivery_time,status,address,phone  FROM
        result.append(context.getSql(), 0, context.getSqlTokens().get(0).getStartIndex());
        // 循环所有 Token
        for (SQLToken each : context.getSqlTokens()) {
            if (each instanceof ComposableSQLToken) {
                result.append(getComposableSQLTokenText((ComposableSQLToken) each));
            } else if (each instanceof SubstitutableColumnNameToken) {
                result.append(((SubstitutableColumnNameToken) each).toString(routeUnit));
            } else {
                // 添加 令牌对应的改写SQL
                result.append(getSQLTokenText(each));
            }
            result.append(getConjunctionText(each));
        }
        return result.toString();
    }
    
    protected abstract String getSQLTokenText(SQLToken sqlToken);
    
    private String getComposableSQLTokenText(final ComposableSQLToken composableSQLToken) {
        StringBuilder result = new StringBuilder();
        for (SQLToken each : composableSQLToken.getSqlTokens()) {
            result.append(getSQLTokenText(each));
            result.append(getConjunctionText(each));
        }
        return result.toString();
    }
    
    private String getConjunctionText(final SQLToken sqlToken) {
        return context.getSql().substring(getStartIndex(sqlToken), getStopIndex(sqlToken));
    }
    
    private int getStartIndex(final SQLToken sqlToken) {
        int startIndex = sqlToken instanceof Substitutable ? ((Substitutable) sqlToken).getStopIndex() + 1 : sqlToken.getStartIndex();
        return Math.min(startIndex, context.getSql().length());
    }
    
    private int getStopIndex(final SQLToken sqlToken) {
        int currentSQLTokenIndex = context.getSqlTokens().indexOf(sqlToken);
        return context.getSqlTokens().size() - 1 == currentSQLTokenIndex ? context.getSql().length() : context.getSqlTokens().get(currentSQLTokenIndex + 1).getStartIndex();
    }
}
