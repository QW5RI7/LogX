package io.github.qw5ri7.logx.mapper;

import io.github.qw5ri7.logx.model.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface GameUserMapper {
    @Update("CREATE TABLE IF NOT EXISTS users (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "username TEXT NOT NULL, " +
            "uuid TEXT NOT NULL, " +
            "access_token TEXT, " +
            "refresh_token TEXT, " +
            "account_type TEXT NOT NULL)")
    void createTable();
    @Insert("INSERT INTO users (username, uuid, access_token, refresh_token, account_type) VALUES (#{username}, #{uuid}, #{accessToken}, #{refreshToken}, #{accountType})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertUser(User user);
    @Update("UPDATE users SET access_token = #{accessToken}, refresh_token = #{refreshToken} WHERE id = #{id}")
    void updateUserTokens(User user);
    @Delete("DELETE FROM users WHERE id = #{id}")
    void deleteUserById(int id);
    @Select("SELECT * FROM users")
    List<User> getAllUsers();
    @Select("SELECT * FROM users WHERE id = #{id}")
    User getUserById(int id);
    @Select("SELECT * FROM users WHERE username = #{username}")
    User getUserByUsername(String username);

}
