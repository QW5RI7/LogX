package io.github.qw5ri7.logx;

import io.github.qw5ri7.logx.mapper.GameUserMapper;
import io.github.qw5ri7.logx.util.MyBatisUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import javax.swing.*;
import java.io.File;

@Slf4j
public class Launcher {
    public static final File APP_DIR = new File(System.getProperty("user.home"), ".logx");
    public static void main(String[] args) {
        try {
            Bootstrap.init();
        } catch (Exception e) {
            String msg = e.getMessage();
            log.error(msg, e);
            JOptionPane.showMessageDialog(null, msg, "启动失败", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        Main.main(args);
    }
}
