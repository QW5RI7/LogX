package io.github.qw5ri7.logx.auth;

import io.github.qw5ri7.logx.mapper.GameUserMapper;
import io.github.qw5ri7.logx.model.AccountType;
import io.github.qw5ri7.logx.model.User;
import io.github.qw5ri7.logx.util.MyBatisUtil;
import org.apache.ibatis.session.SqlSession;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class OfflineAuthenticator {
    public static User login(String username) {
        User user;
        try (SqlSession sqlSession = MyBatisUtil.getFactory().openSession(true)) {
            GameUserMapper userMapper = sqlSession.getMapper(GameUserMapper.class);
            user = userMapper.getUserByUsername(username);
            if (user == null) {
                String offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
                user = new User(null, username, "12345"
                        , "123456", offlineUuid, AccountType.LOCAL);
                userMapper.insertUser(user);
            }
        }
        return user;
    }
}
