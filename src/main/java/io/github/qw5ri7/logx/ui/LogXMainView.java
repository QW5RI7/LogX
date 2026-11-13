package io.github.qw5ri7.logx.ui;

import com.jfoenix.controls.JFXButton;
import io.github.qw5ri7.logx.util.ConfigUtil;
import io.github.qw5ri7.logx.util.SVGUtil;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.util.Random;

@Slf4j
public class LogXMainView {
    private boolean playRestoreMinimizeAnimation = false;

    private final StackPane outerContainer; // 最外层：阴影留白
    private final BorderPane rootLayout;    // 核心布局
    private final StackPane centerContent;  // 页面切换区

    private double xOffset = 0;
    private double yOffset = 0;
    private final Stage stage;
    private Node currentCenterPanel;

    public LogXMainView(Stage stage) {
        this.stage = stage;
        this.stage.initStyle(StageStyle.TRANSPARENT);

        // ================= 1. 核心 BorderPane =================
        this.rootLayout = new BorderPane();
        // 底层加白底防隐身，加圆角
        this.rootLayout.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-background-radius: 8; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 15, 0, 0, 5);"
        );

        // 顶部标题栏 (纯蓝色)
        HBox topBar = createTopBar();
        topBar.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.8); " + // 磨砂白，和左侧导航呼应
                        "-fx-border-color: rgba(255, 255, 255, 0.1); " +     // 底部加一条极淡的分割线
                        "-fx-border-width: 0 0 1 0;"                         // 仅在底部有线条
        );
        this.rootLayout.setTop(topBar);

        // ================= 2. 下半部分（包含背景、左侧、中间） =================
        StackPane bottomArea = new StackPane();
        bottomArea.setMinSize(200, 200);
        boolean isImageBackground = false;
        // (1) 背景图
        Image bgImage = loadBackgroundImage();
        if (bgImage != null) {
            ImageView bgView = new ImageView(bgImage);
            bgView.setPreserveRatio(false);
            bgView.fitWidthProperty().bind(bottomArea.widthProperty());
            bgView.fitHeightProperty().bind(bottomArea.heightProperty());
            bottomArea.getChildren().add(bgView);
            isImageBackground = true;
        } else {
            // 兜底渐变
            bottomArea.setStyle("-fx-background-color: linear-gradient(to bottom right, #ffffff, #94DFF7);");
        }

        // (2) 遮罩层 (半透明黑色，让白色文字清晰)
        Region overlay = new Region();
        if (isImageBackground) {
            overlay.setStyle("-fx-background-color: rgba(120, 120, 120, 0.4);");
        }
        bottomArea.getChildren().add(overlay);

        // (3) 内容层 (包含左侧导航和中间内容)
        BorderPane contentWrapper = new BorderPane();
        contentWrapper.setStyle("-fx-background-color: transparent;");

        VBox leftNav = createLeftNav();
        if (isImageBackground) {
            leftNav.setStyle("-fx-background-color: rgba(255, 255, 255, 0.5);");
        } else {
            // 没图时，给个非常淡的灰色
            leftNav.setStyle("-fx-background-color: #f1f5f9;");
        }
        this.centerContent = new StackPane();
        this.centerContent.setStyle("-fx-background-color: transparent;");

        contentWrapper.setLeft(leftNav);
        contentWrapper.setCenter(this.centerContent);

        // 叠加：底图 -> 遮罩 -> 内容
        bottomArea.getChildren().add(contentWrapper);

        this.rootLayout.setCenter(bottomArea);

        // ================= 3. 外层与裁剪 =================
        Rectangle clip = new Rectangle();
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        clip.widthProperty().bind(this.rootLayout.widthProperty());
        clip.heightProperty().bind(this.rootLayout.heightProperty());
        this.rootLayout.setClip(clip);

        this.outerContainer = new StackPane(this.rootLayout);
        this.outerContainer.setPadding(new Insets(10));
        this.outerContainer.setStyle("-fx-background-color: rgba(0,0,0,0.0);");

        // ================= 4. 默认页面 =================
        Label defaultPage = new Label("这里是 启动 页面");
        defaultPage.setTextFill(Color.WHITE);
        defaultPage.setStyle("-fx-font-size: 24px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 10, 0, 0, 2);");
        switchCenterPanel(defaultPage);

        this.stage.iconifiedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && playRestoreMinimizeAnimation) {
                Platform.runLater(this::playRestoreAnimation);
            }
        });
    }

    public Parent getRoot() {
        return outerContainer;
    }

    // --- 顶部 ---
    private HBox createTopBar() {
        HBox topBar = new HBox();
        topBar.setPrefHeight(48);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 10, 0, 20));

        Label logoLabel = new Label("LogX");
        logoLabel.setFont(new Font(20));
        logoLabel.setTextFill(Color.BLACK);
        logoLabel.setStyle("-fx-font-weight: bold;");

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        JFXButton btnHelp = createTitleButton("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><path d=\"M9 9.5a3 2.5 0 016 0c0 2-2 2-3 3.5\"/><path d=\"M12 17h.01\"/></svg>");

        JFXButton btnClose = createTitleButton("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-x preview-icon\"><path d=\"M18 6 6 18\"/><path d=\"m6 6 12 12\"/></svg>");
        JFXButton btnMin = createTitleButton("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n" +
                "  <line x1=\"3\" y1=\"12\" x2=\"21\" y2=\"12\" />\n" +
                "</svg>");

        btnMin.setOnAction(e -> minimizeWindow());
        btnClose.setOnAction(e -> closeWindow());

        topBar.getChildren().addAll(logoLabel, topSpacer, btnHelp, btnMin, btnClose);

        topBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        topBar.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });

        return topBar;
    }

    // --- 左侧 ---
    private VBox createLeftNav() {
        VBox leftNav = new VBox(10);
        leftNav.setPrefWidth(50);
        leftNav.setMinWidth(50);
        leftNav.setAlignment(Pos.TOP_CENTER);
        leftNav.setPadding(new Insets(10, 0, 10, 0));

        JFXButton btnLaunch = createNavButton("启动", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z\"/></svg>");
        JFXButton btnDownload = createNavButton("下载", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M12 15V3\"/><path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\"/><path d=\"m7 10 5 5 5-5\"/></svg>");
        JFXButton btnTools = createNavButton("工具", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.106-3.105c.32-.322.863-.22.983.218a6 6 0 0 1-8.259 7.057l-7.91 7.91a1 1 0 0 1-2.999-3l7.91-7.91a6 6 0 0 1 7.057-8.259c.438.12.54.662.219.984z\"/></svg>");

        Region leftSpacer = new Region();
        VBox.setVgrow(leftSpacer, Priority.ALWAYS);

        JFXButton btnSettings = createNavButton("设置", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915\"/><circle cx=\"12\" cy=\"12\" r=\"3\"/></svg>");

        leftNav.getChildren().addAll(btnLaunch, btnDownload, btnTools, leftSpacer, btnSettings);

        Label pageHome = new Label("这里是 启动 页面");
        pageHome.setTextFill(Color.WHITE);
        Label pageDownload = new Label("这里是 下载 页面");
        pageDownload.setTextFill(Color.WHITE);
        Label pageTools = new Label("这里是 工具 页面");
        pageTools.setTextFill(Color.WHITE);
        Label pageSettings = new Label("这里是 设置 页面");
        pageSettings.setTextFill(Color.WHITE);

        btnLaunch.setOnAction(e -> switchCenterPanel(pageHome));
        btnDownload.setOnAction(e -> switchCenterPanel(pageDownload));
        btnTools.setOnAction(e -> switchCenterPanel(pageTools));
        btnSettings.setOnAction(e -> switchCenterPanel(pageSettings));

        return leftNav;
    }

    private JFXButton createTitleButton(String svgSource) {
        JFXButton btn = new JFXButton();
        btn.setGraphic(SVGUtil.parseSvg(svgSource, 24));
        btn.setPrefSize(40, 30);
        btn.setFocusTraversable(false);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffffff; -fx-cursor: hand;");
        btn.setOnMouseEntered(e ->
                btn.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-cursor: hand; -fx-background-radius: 5;")
        );
        btn.setOnMouseExited(e ->
                btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;")
        );
        return btn;
    }

    private JFXButton createNavButton(String text, String svgSource) {
        Node icon = SVGUtil.parseSvg(svgSource, 22);
        SVGUtil.changeColor(icon, Color.BLACK);

        VBox content = new VBox(3);
        content.setAlignment(Pos.CENTER);
        Label label = new Label(text);
        label.setTextFill(Color.BLACK);
        content.getChildren().addAll(icon, label);

        JFXButton btn = new JFXButton();
        btn.setFocusTraversable(false);
        btn.setGraphic(content);
        btn.setPrefSize(65, 65);
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-background-radius: 10;");

        return btn;
    }

    public void switchCenterPanel(Node newPanel) {
        if (currentCenterPanel == newPanel) return;
        this.centerContent.getChildren().clear();
        this.centerContent.getChildren().add(newPanel);
        this.currentCenterPanel = newPanel;
    }
    /**
     * 窗口动画，参考了HMCL的窗口动画代码：
     * <a href="https://github.com/HMCL-dev/HMCL">HMCL GitHub仓库</a>
    */
    public void playOpenAnimation() {
        Parent root = getRoot();
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(root.opacityProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(root.scaleXProperty(), 0.8, Interpolator.EASE_BOTH),
                        new KeyValue(root.scaleYProperty(), 0.8, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(300),
                        new KeyValue(root.opacityProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(root.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(root.scaleYProperty(), 1, Interpolator.EASE_BOTH))
        );
        timeline.jumpTo(Duration.ZERO);
        timeline.play();
    }

    public void minimizeWindow() {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(rootLayout.opacityProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(rootLayout.translateYProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(rootLayout.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(rootLayout.scaleYProperty(), 1, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(rootLayout.opacityProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(rootLayout.translateYProperty(), 200, Interpolator.EASE_BOTH),
                        new KeyValue(rootLayout.scaleXProperty(), 0.4, Interpolator.EASE_BOTH),
                        new KeyValue(rootLayout.scaleYProperty(), 0.4, Interpolator.EASE_BOTH))
        );

        timeline.setOnFinished(e -> {
            stage.setIconified(true);
            rootLayout.setOpacity(1);
            rootLayout.setTranslateY(0);
            rootLayout.setScaleX(1);
            rootLayout.setScaleY(1);
        });

        playRestoreMinimizeAnimation = true;
        timeline.play();
    }

    public void playRestoreAnimation() {
        rootLayout.setOpacity(0);
        rootLayout.setTranslateY(200);
        rootLayout.setScaleX(0.4);
        rootLayout.setScaleY(0.4);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(rootLayout.opacityProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(rootLayout.translateYProperty(), 200, Interpolator.EASE_BOTH),
                        new KeyValue(rootLayout.scaleXProperty(), 0.4, Interpolator.EASE_BOTH),
                        new KeyValue(rootLayout.scaleYProperty(), 0.4, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(rootLayout.opacityProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(rootLayout.translateYProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(rootLayout.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(rootLayout.scaleYProperty(), 1, Interpolator.EASE_BOTH))
        );

        timeline.setOnFinished(e -> {
            rootLayout.setOpacity(1);
            rootLayout.setTranslateY(0);
            rootLayout.setScaleX(1);
            rootLayout.setScaleY(1);
            playRestoreMinimizeAnimation = false;
        });
        timeline.play();
    }

    public void closeWindow() {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(outerContainer.opacityProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(outerContainer.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(outerContainer.scaleYProperty(), 1, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(150),
                        new KeyValue(outerContainer.opacityProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(outerContainer.scaleXProperty(), 0.8, Interpolator.EASE_BOTH),
                        new KeyValue(outerContainer.scaleYProperty(), 0.8, Interpolator.EASE_BOTH))
        );
        timeline.setOnFinished(e -> {
            stage.close();
            Platform.exit();
            System.exit(0);
        });
        timeline.play();
    }

    private Image loadBackgroundImage() {
        String customPath = ConfigUtil.getCurrentConfig().getBackgroundPath();
        if (customPath != null && !customPath.trim().isEmpty()) {
            File file = new File(customPath);
            if (file.exists()) {
                try {
                    return new Image(file.toURI().toString(), 1920, 0, true, true, true);
                } catch (Exception e) {
                    log.error("加载自定义背景失败", e);
                }
            }
        }
        int randomIndex = new Random().nextInt(4) + 1;
        String defaultBgPath = "/images/backgrounds/bg" + randomIndex + ".jpg";
        try (InputStream is = getClass().getResourceAsStream(defaultBgPath)) {
            if (is != null) return new Image(is);
        } catch (Exception e) {
            log.error("加载内置背景图失败", e);
        }
        return null;
    }
}