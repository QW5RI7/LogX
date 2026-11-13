package io.github.qw5ri7.logx;

import io.github.qw5ri7.logx.model.LauncherConfig;
import io.github.qw5ri7.logx.ui.LogXMainView;
import io.github.qw5ri7.logx.ui.ResizeHelper;
import io.github.qw5ri7.logx.util.ConfigUtil;
import javafx.animation.*;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.util.Random;
@Slf4j
// TODO：UI层面暂未实现
public class Main extends Application {
    private static final double MIN_WIDTH = 800;
    private static final double MIN_HEIGHT = 500;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("LogX 启动器");
        LogXMainView mainView = new LogXMainView(primaryStage);
        ChangeListener<Number> windowStateListener = (obs, oldVal, newVal) -> {
            LauncherConfig config = ConfigUtil.getCurrentConfig();
            config.setWidth(primaryStage.getWidth());
            config.setHeight(primaryStage.getHeight());
            config.setX(primaryStage.getX());
            config.setY(primaryStage.getY());
        };
        LauncherConfig config = ConfigUtil.getCurrentConfig();
        double width = config.getWidth() != null && config.getWidth() > 0 ? config.getWidth() : 900;
        double height = config.getHeight() != null && config.getHeight() > 0 ? config.getHeight() : 600;
        if (width < MIN_WIDTH) width = MIN_WIDTH;
        if (height < MIN_HEIGHT) height = MIN_HEIGHT;
        primaryStage.setWidth(width);
        primaryStage.setHeight(height);

        Double x = config.getX();
        Double y = config.getY();
        Screen screen = Screen.getPrimary();
        Rectangle2D bounds = screen.getVisualBounds();
        // 如果保存的 x/y 超出了当前屏幕，就重置为居中
        if (x < bounds.getMinX() || x > bounds.getMaxX() - 100) x = -1.0;
        if (y < bounds.getMinY() || y > bounds.getMaxY() - 100) y = -1.0;
        if (x != -1 && y != -1) {
            primaryStage.setX(x);
            primaryStage.setY(y);
        } else {
            // 如果是 -1，不设置 setX 和 setY，JavaFX 会默认居中（或者调用 centerOnScreen）
            primaryStage.centerOnScreen();
        }
        primaryStage.widthProperty().addListener(windowStateListener);
        primaryStage.heightProperty().addListener(windowStateListener);
        primaryStage.xProperty().addListener(windowStateListener);
        primaryStage.yProperty().addListener(windowStateListener);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(500);
        Scene scene = new Scene(mainView.getRoot(), width, height);
        scene.setFill(Color.TRANSPARENT);
        mainView.getRoot().setOpacity(0);
        mainView.getRoot().setScaleX(0.8);
        mainView.getRoot().setScaleY(0.8);
        primaryStage.setScene(scene);
        ResizeHelper.addResizeListener(primaryStage);
        primaryStage.show();
        javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(50));
        delay.setOnFinished(event -> {
            mainView.playOpenAnimation();
        });
        delay.play();
    }
    public static void main(String[] args) {
        launch(args);
    }
}
