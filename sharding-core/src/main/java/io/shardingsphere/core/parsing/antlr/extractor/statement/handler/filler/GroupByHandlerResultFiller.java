/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.core.parsing.antlr.extractor.statement.handler.filler;

import com.google.common.base.Optional;

import io.shardingsphere.core.constant.OrderDirection;
import io.shardingsphere.core.metadata.table.ShardingTableMetaData;
import io.shardingsphere.core.parsing.antlr.extractor.statement.handler.result.GroupByExtractResult;
import io.shardingsphere.core.parsing.parser.context.OrderItem;
import io.shardingsphere.core.parsing.parser.sql.SQLStatement;
import io.shardingsphere.core.parsing.parser.sql.dql.select.SelectStatement;
import io.shardingsphere.core.rule.ShardingRule;

/**
 * Group by handler result filler.
 * 
 * @author duhongjun
 */
public class GroupByHandlerResultFiller extends AbstractHandlerResultFiller {
    
    public GroupByHandlerResultFiller() {
        super(GroupByExtractResult.class);
    }
    
    @Override
    protected void fillSQLStatement(final Object extractResult, final SQLStatement statement, final ShardingRule shardingRule, final ShardingTableMetaData shardingTableMetaData) {
        GroupByExtractResult orderExtractResult = (GroupByExtractResult) extractResult;
        SelectStatement selectStatement = (SelectStatement) statement;
        if(-1 < orderExtractResult.getIndex()) {
            selectStatement.getOrderByItems().add(new OrderItem(orderExtractResult.getIndex(), OrderDirection.ASC, OrderDirection.ASC)); 
        }else if(orderExtractResult.getName().isPresent()) {
            String owner = orderExtractResult.getOwner().isPresent() ? orderExtractResult.getOwner().get() : "";
            String name = orderExtractResult.getName().isPresent() ? orderExtractResult.getName().get() : "";
            selectStatement.getOrderByItems().add(new OrderItem(owner, name, OrderDirection.ASC, OrderDirection.ASC, Optional.<String>absent())); 
        }
    }
}
