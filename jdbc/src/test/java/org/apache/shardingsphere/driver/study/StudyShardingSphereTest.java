package org.apache.shardingsphere.driver.study;

import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.apache.shardingsphere.driver.jdbc.core.datasource.ShardingSphereDataSource;
import org.apache.shardingsphere.infra.hint.HintManager;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StudyShardingSphereTest {

    @Test
    void assertJDBC() throws SQLException {
//        testPreparedStatement(true);


        try (HintManager hintManager = HintManager.getInstance()) {
            hintManager.setWriteRouteOnly();
//
//            hintManager.setReadwriteSplittingAuto();

//            hintManager.addDatabaseShardingValue("t_order", 2L);
//            hintManager.setDataSourceName("db" + "1");

            testPreparedStatement(true);

//            testBindStatement();

//            testStatement();



        }

    }

    private void testPreparedStatement(Boolean autoCommit) throws SQLException {
//        SELECT * FROM t_order where user_id = 1
        Connection connection = DriverManager.getConnection("jdbc:shardingsphere:classpath:config/study/sharding-px.yaml");

        connection.setAutoCommit(autoCommit);

//        int insertId = ThreadLocalRandom.current().nextInt(1, 100);
//        PreparedStatement preparedInsert = connection
//                .prepareStatement("INSERT INTO `t_order` (`id`, `order_no`, `user_id`, `amount`) VALUES (?, ?, ?, ?)");
//        preparedInsert.setLong(1, insertId);
//        preparedInsert.setString(2, "order_no_" + insertId);
//        preparedInsert.setLong(3, 1L);
//        preparedInsert.setDouble(4, 3.12);
//        preparedInsert.executeUpdate();
//
//        System.out.println("==========> insert执行完, id: " + insertId);


        PreparedStatement pstmt = connection.prepareStatement("SELECT * FROM t_order where user_id = ?");


        pstmt.setLong(1, 1L);
        ResultSet resultSet = pstmt.executeQuery();
        while (resultSet.next()) {
            Long id = resultSet.getLong("id");
            Long user_id = resultSet.getLong("user_id");
            String order_no = resultSet.getString("order_no");
            System.out.println("=========> id: " + id + ", user_id: " + user_id + ",order_no: " + order_no);
        }

        if (!autoCommit) {
            connection.commit();
        }

        resultSet.close();
        pstmt.close();
        connection.close();

        System.out.println("<=====================================================>");
    }


    private void testStatement() throws SQLException {
//        String sql = "SELECT * FROM t_order where user_id =4 and order_no = '1'";
//        String sql = "SELECT * FROM t_order where user_id =4 and order_no = 'ATGUIGU003' and id=997253951617236992";
//        String sql = "SELECT * FROM t_order where user_id =4 or order_no = '1'";
        String sql = "SELECT * FROM t_order where user_id in (3, 4, 5)";

//        String sql = "/* SHARDINGSPHERE_HINT: DISABLE_AUDIT_NAMES=sharding_key_required_auditor, DATA_SOURCE_NAME=db0 */ SELECT * FROM t_order";
//        String sql = "SELECT * FROM t_order where user_id=6 and order_no='4' or amount = 105.00";
//        String sql = "SELECT * FROM t_order";

//        sql = "SELECT * FROM t_order where user_id = 1";

        try (
                Connection connection = DriverManager.getConnection("jdbc:shardingsphere:classpath:config/study/sharding-px.yaml");
                Statement statement = connection.createStatement()) {

            try (ResultSet resultSet = statement.executeQuery(sql)) {
                while (resultSet.next()) {
                    Long id = resultSet.getLong("id");
                    Long user_id = resultSet.getLong("user_id");
                    String order_no = resultSet.getString("order_no");
                    System.out.println("=========> id: " + id + ", user_id: " + user_id + ",order_no: " + order_no);
                }
            }
        }
    }


    private void testBindStatement() throws SQLException {
        String sql = "SELECT o.order_no, SUM(i.price * i.count) AS amount\n" +
                "FROM t_order o\n" +
                "JOIN t_order_item i ON o.order_no = i.order_no\n" +
                "GROUP BY o.order_no";

        try (
                Connection connection = DriverManager.getConnection("jdbc:shardingsphere:classpath:config/study/sharding-px.yaml");
                Statement statement = connection.createStatement()) {
            // org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSphereStatement.executeQuery
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                while (resultSet.next()) {
                    String orderNo = resultSet.getString("order_no");
                    String amount = resultSet.getString("amount");
                    System.out.println("=========> order_no: " + orderNo + ", amount: " + amount);
                }
            }
        }
    }

}
