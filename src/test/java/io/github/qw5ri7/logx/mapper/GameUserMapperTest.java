package io.github.qw5ri7.logx.mapper;

import io.github.qw5ri7.logx.model.AccountType;
import io.github.qw5ri7.logx.model.User;
import io.github.qw5ri7.logx.util.MyBatisUtil;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GameUserMapperTest {
    static SqlSession session;
    static GameUserMapper mapper;

    // 在所有测试开始前执行一次
    @BeforeAll
    static void setup() {
        session = MyBatisUtil.getFactory().openSession(true);
        mapper = session.getMapper(GameUserMapper.class);
        mapper.createTable(); // 保证表一定存在
    }

    // 在所有测试结束后执行一次
    @AfterAll
    static void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void testFullUserLifecycle() {
        // 1. 准备数据
        User user = new User();
        user.setUsername("mc_test");
        user.setUuid("12345");
        user.setAccessToken("12def");
        user.setRefreshToken("12345");
        user.setAccountType(AccountType.LOCAL);

        // 2. 测试插入
        mapper.insertUser(user);
        // ★ 必须在 Mapper 的 insertUser 上加 @Options，否则这里会报错！
        assertNotNull(user.getId(), "插入后 ID 必须回填");
        System.out.println("插入成功，生成的 ID 是: " + user.getId());

        // 3. 测试根据 ID 查询
        User dbUser = mapper.getUserById(user.getId());
        assertNotNull(dbUser);
        assertEquals("mc_test", dbUser.getUsername());
        System.out.println("查询成功：" + dbUser);

        // 4. 测试更新
        dbUser.setAccessToken("new_token_666");
        mapper.updateUserTokens(dbUser);
        User updatedUser = mapper.getUserById(user.getId());
        assertEquals("new_token_666", updatedUser.getAccessToken(), "Token 更新失败");

        // 5. 测试查询所有
        List<User> users = mapper.getAllUsers();
        assertFalse(users.isEmpty());
        System.out.println("当前所有用户数量：" + users.size());

        // 6. 测试删除
        mapper.deleteUserById(user.getId());
        assertNull(mapper.getUserById(user.getId()), "删除后应该查不到数据");
        System.out.println("测试全部通过！");
    }
}