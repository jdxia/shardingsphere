package org.apache.shardingsphere.driver.study;


import org.apache.shardingsphere.infra.algorithm.core.context.AlgorithmSQLContext;
import org.apache.shardingsphere.infra.algorithm.keygen.core.KeyGenerateAlgorithm;
import org.apache.shardingsphere.infra.spi.annotation.SingletonSPI;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

@SingletonSPI  // 可加可不加
public final class MyIdGen implements KeyGenerateAlgorithm {

    private final AtomicLong counter = new AtomicLong();

    private static final String TYPENAME = "MyIdGen";

    private Properties props;

    // 初始化属性
    @Override
    public void init(Properties props) {
        KeyGenerateAlgorithm.super.init(props);

        // 配置文件设置的这边可以拿到
        this.props = props;
    }

    @Override
    public Collection<Long> generateKeys(final AlgorithmSQLContext context, final int keyGenerateCount) {
        Collection<Long> result = new LinkedList<>();
        for (int index = 0; index < keyGenerateCount; index++) {
            result.add(generateKey());
        }
        return result;
    }

    private long generateKey() {
        return counter.incrementAndGet();
    }

    @Override
    public String getType() {
        return TYPENAME;
    }


    @Override
    public boolean isSupportAutoIncrement() {
        return KeyGenerateAlgorithm.super.isSupportAutoIncrement();
    }


}
