package io.github.qw5ri7.logx.util;

import io.github.qw5ri7.logx.Launcher;
import io.github.qw5ri7.logx.mapper.GameUserMapper;
import lombok.Getter;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import java.io.File;

/**
 * MyBatis 工具类，用于创建 SqlSessionFactory
 */
public class MyBatisUtil {
    /**
     * SqlSessionFactory 实例，用于创建数据库会话
     */
    @Getter
    private static final SqlSessionFactory factory;

    static {
        File dbFile = new File(Launcher.APP_DIR, "launcher_data.db");
        String dbPath = dbFile.getAbsolutePath();
        PooledDataSource dataSource = new PooledDataSource(
                "org.sqlite.JDBC",
                "jdbc:sqlite:" + dbPath,
                null, null
        );

        // 2. 构建环境
        Environment environment = new Environment("development", new JdbcTransactionFactory(), dataSource);

        // 3. 创建核心配置对象
        Configuration config = new Configuration(environment);
        config.setMapUnderscoreToCamelCase(true); // 开启驼峰命名映射
        // 4.添加Mapper接口
        config.addMapper(GameUserMapper.class);
        // 5. 构建工厂
        factory = new SqlSessionFactoryBuilder().build(config);
    }

}