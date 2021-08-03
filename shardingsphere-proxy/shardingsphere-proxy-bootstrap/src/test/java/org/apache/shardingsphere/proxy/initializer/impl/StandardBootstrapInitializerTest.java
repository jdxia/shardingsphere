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

package org.apache.shardingsphere.proxy.initializer.impl;

import org.apache.shardingsphere.infra.config.persist.repository.DistMetaDataPersistRepository;
import org.apache.shardingsphere.infra.context.metadata.MetaDataContexts;
import org.apache.shardingsphere.infra.spi.ShardingSphereServiceLoader;
import org.apache.shardingsphere.infra.yaml.config.swapper.YamlRuleConfigurationSwapper;
import org.apache.shardingsphere.transaction.context.TransactionContexts;
import org.apache.shardingsphere.transaction.core.XATransactionManagerType;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public final class StandardBootstrapInitializerTest extends AbstractBootstrapInitializerTest {
    
    @Test
    public void assertDecorateMetaDataContexts() {
        MetaDataContexts metaDataContexts = mock(MetaDataContexts.class);
        assertThat(getInitializer().decorateMetaDataContexts(metaDataContexts), is(metaDataContexts));
    }
    
    @Test
    public void assertDecorateTransactionContexts() {
        TransactionContexts transactionContexts = mock(TransactionContexts.class);
        TransactionContexts actualTransactionContexts = getInitializer().decorateTransactionContexts(transactionContexts, XATransactionManagerType.ATOMIKOS.getType());
        assertNotNull(actualTransactionContexts);
        assertThat(actualTransactionContexts.getEngines(), is(transactionContexts.getEngines()));
        assertThat(actualTransactionContexts.getDefaultTransactionManagerEngine(), is(transactionContexts.getDefaultTransactionManagerEngine()));
    }
    
    protected void doEnvironmentPrepare() {
        ShardingSphereServiceLoader.register(YamlRuleConfigurationSwapper.class);
    }
    
    protected void prepareSpecifiedInitializer() {
        setInitializer(new StandardBootstrapInitializer(mock(DistMetaDataPersistRepository.class)));
    }
}
