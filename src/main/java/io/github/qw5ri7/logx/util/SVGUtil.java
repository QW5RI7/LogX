package io.github.qw5ri7.logx.util;

import javafx.geometry.Bounds;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标准的 SVG 解析 / 渲染工具，把 SVG 文档转换成 JavaFX 的 {@link Node}。
 *
 * <p>不再依赖正则表达式去抓 {@code d="..."}，而是用 JDK 自带的 DOM 解析器读取完整的
 * SVG 文档树，再逐个元素映射到 JavaFX 图形节点。支持的内容如下：</p>
 *
 * <ul>
 *     <li>图形元素：{@code path}、{@code rect}、{@code circle}、{@code ellipse}、
 *     {@code line}、{@code polyline}、{@code polygon}、{@code text}</li>
 *     <li>容器元素：{@code svg}（含嵌套）、{@code g}、{@code a}、{@code switch}、
 *     {@code use}（引用 {@code symbol} / 任意元素）</li>
 *     <li>视口：{@code width} / {@code height} / {@code viewBox} / {@code preserveAspectRatio}
 *     （{@code meet}、{@code slice}、{@code none}）</li>
 *     <li>坐标变换：{@code transform="translate scale rotate matrix skewX skewY"}</li>
 *     <li>样式：表现属性（presentation attributes）、内联 {@code style}、
 *     {@code <style>} 里简单的 CSS 规则（标签 / {@code .class} / {@code #id}）</li>
 *     <li>颜色：命名颜色、{@code #rgb/#rrggbb}、{@code rgb()/rgba()/hsl()/hsb()}、
 *     {@code currentColor}、{@code linearGradient} / {@code radialGradient}、
 *     {@code fill-opacity} / {@code stroke-opacity}</li>
 *     <li>描边：{@code stroke-width}、{@code stroke-linecap}、{@code stroke-linejoin}、
 *     {@code stroke-miterlimit}、{@code stroke-dasharray}、{@code stroke-dashoffset}</li>
 *     <li>{@code fill-rule}、{@code opacity}、{@code display}、{@code visibility}</li>
 * </ul>
 *
 * <p>为安全起见，解析时会禁用外部实体与外部 DTD 加载（防 XXE），
 * 同时仍然允许标准的 {@code <!DOCTYPE svg ...>} 声明。</p>
 *
 * <p>暂不支持：{@code filter}、{@code mask}、{@code clipPath}、{@code pattern}、
 * {@code marker}、动画（SMIL）、{@code image} 以及外部文件引用。遇到这些元素会被忽略，
 * 不会影响其余内容的渲染。</p>
 *
 * <p>默认保留 SVG 自带的颜色（含渐变）；需要单色图标时，解析完再调用
 * {@link #changeColor(Node, Color)} 即可自动识别填充 / 描边并换色。</p>
 *
 * <p>所有返回的节点都已经把 {@code viewBox} 缩放到目标尺寸，{@code layoutBounds}
 * 恒为 {@code (0, 0, 目标宽, 目标高)}，因此不同图标放进同一个容器时能按同一套网格对齐。
 * 按照 SVG 的默认行为，超出视口的内容会被裁剪；根元素写 {@code overflow="visible"} 可以关闭裁剪。
 * 直接传入非 {@code <svg>} 根元素的图形片段时，会按图形自身的包围盒输出。</p>
 */
@Slf4j
public final class SVGUtil {

    /** xlink 命名空间（旧版 SVG 里引用渐变 / use 时使用）。 */
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    /** 没有 width / height / viewBox 时的默认视口。 */
    private static final double DEFAULT_WIDTH = 300;
    private static final double DEFAULT_HEIGHT = 150;

    /** 透明视口矩形的标记，换色时跳过它，避免把整个图标框填满。 */
    private static final String VIEWPORT_MARKER = "SVGUtil.viewport";

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");
    private static final Pattern LENGTH_PATTERN = Pattern.compile(
            "^([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*([a-zA-Z%]*)$");
    private static final Pattern TRANSFORM_PATTERN =
            Pattern.compile("([A-Za-z]+)\\s*\\(([^)]*)\\)");
    private static final Pattern SELECTOR_PATTERN =
            Pattern.compile("^([A-Za-z][\\w-]*|\\*)?((?:[.#][\\w-]+)*)$");

    private SVGUtil() {
    }

    // ==================================================================
    // 公共 API
    // ==================================================================

    /**
     * 解析 SVG 文件，输出尺寸使用 SVG 自身声明的尺寸（没有声明时用 viewBox 的尺寸）。
     *
     * @param svgFile 本地 SVG 文件
     * @return JavaFX Node，解析失败时返回一个占位节点
     */
    public static Node loadSvg(File svgFile) {
        return loadSvg(svgFile, 0);
    }

    /**
     * 解析 SVG 文件，并等比缩放到指定边长（图标常用）。
     *
     * @param svgFile 本地 SVG 文件
     * @param size    输出方框的边长（像素）；{@code <= 0} 表示使用 SVG 原始尺寸
     * @return JavaFX Node
     */
    public static Node loadSvg(File svgFile, double size) {
        return loadSvg(svgFile, size, size);
    }
    private static final Map<String, Node> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * 解析 SVG 文件，并等比缩放到指定宽高。
     *
     * @param svgFile 本地 SVG 文件
     * @param width   输出宽度（像素）；{@code <= 0} 表示使用 SVG 原始宽度
     * @param height  输出高度（像素）；{@code <= 0} 表示使用 SVG 原始高度
     * @return JavaFX Node
     */
    public static Node loadSvg(File svgFile, double width, double height) {
        if (svgFile == null || !svgFile.isFile()) {
            log.warn("SVG 文件不存在: {}", svgFile);
            return placeholder(width, height);
        }
        try {
            byte[] bytes = Files.readAllBytes(svgFile.toPath());
            return build(new InputSource(new ByteArrayInputStream(bytes)), svgFile.getName(), width, height);
        } catch (Exception e) {
            log.error("解析 SVG 失败: {}", svgFile.getName(), e);
            return placeholder(width, height);
        }
    }

    /**
     * 解析一段标准 SVG 文本，输出尺寸使用 SVG 自身声明的尺寸。
     *
     * @param svgContent SVG 源码字符串
     * @return JavaFX Node
     */
    public static Node parseSvg(String svgContent) {
        return parseSvg(svgContent, 0, 0);
    }

    /**
     * 解析一段标准 SVG 文本，并等比缩放到指定边长（图标常用）。
     *
     * @param svgContent SVG 源码字符串
     * @param size       输出方框的边长（像素）；{@code <= 0} 表示使用 SVG 原始尺寸
     * @return JavaFX Node
     */
    public static Node parseSvg(String svgContent, double size) {
        return parseSvg(svgContent, size, size);
    }

    /**
     * 解析一段标准 SVG 文本，并等比缩放到指定宽高。
     *
     * @param svgContent SVG 源码字符串
     * @param width      输出宽度（像素）；{@code <= 0} 表示使用 SVG 原始宽度
     * @param height     输出高度（像素）；{@code <= 0} 表示使用 SVG 原始高度
     * @return JavaFX Node
     */
    public static Node parseSvg(String svgContent, double width, double height) {
        if (svgContent == null || svgContent.isBlank()) {
            log.warn("SVG 内容为空");
            return placeholder(width, height);
        }
        String cacheKey = width + "_" + height + "_" + svgContent.hashCode();

        if (CACHE.containsKey(cacheKey)) {
            // 注意：缓存返回的 Node 不能直接 add 到多个父节点，必须深拷贝！
            return cloneNode(CACHE.get(cacheKey));
        }
        try {
            String xml = stripBom(svgContent);
            Node node = build(new InputSource(new StringReader(xml)), "SVG 文本", width, height);
            CACHE.put(cacheKey, node);
            return node;
        } catch (Exception e) {
            log.error("解析 SVG 文本失败", e);
            return placeholder(width, height);
        }
    }
    /**
     * 深度克隆 JavaFX Node 树
     */
    public static Node cloneNode(Node node) {
        if (node == null) return null;

        Node copy = null;

        // 1. 处理容器
        if (node instanceof Group group) {
            Group g = new Group();
            for (Node child : group.getChildren()) {
                g.getChildren().add(cloneNode(child));
            }
            copy = g;
        }
        // 2. 处理各种基础形状
        else if (node instanceof SVGPath path) {
            SVGPath p = new SVGPath();
            p.setContent(path.getContent());
            p.setFillRule(path.getFillRule());
            copy = p;
        } else if (node instanceof Rectangle rect) {
            Rectangle r = new Rectangle(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
            r.setArcWidth(rect.getArcWidth());
            r.setArcHeight(rect.getArcHeight());
            copy = r;
        } else if (node instanceof Circle circle) {
            copy = new Circle(circle.getCenterX(), circle.getCenterY(), circle.getRadius());
        } else if (node instanceof Ellipse ellipse) {
            copy = new Ellipse(ellipse.getCenterX(), ellipse.getCenterY(), ellipse.getRadiusX(), ellipse.getRadiusY());
        } else if (node instanceof Line line) {
            copy = new Line(line.getStartX(), line.getStartY(), line.getEndX(), line.getEndY());
        } else if (node instanceof Polyline polyline) {
            Polyline p = new Polyline();
            p.getPoints().addAll(polyline.getPoints());
            copy = p;
        } else if (node instanceof Polygon polygon) {
            Polygon p = new Polygon();
            p.getPoints().addAll(polygon.getPoints());
            copy = p;
        } else if (node instanceof Text text) {
            Text t = new Text(text.getText());
            t.setX(text.getX());
            t.setY(text.getY());
            t.setFont(text.getFont());
            t.setTextOrigin(text.getTextOrigin());
            copy = t;
        }

        // 3. 如果是 Shape（继承自 Shape 的都有这些属性）
        if (copy instanceof Shape shape && node instanceof Shape originalShape) {
            shape.setFill(originalShape.getFill());
            shape.setStroke(originalShape.getStroke());
            shape.setStrokeWidth(originalShape.getStrokeWidth());
            shape.setStrokeLineCap(originalShape.getStrokeLineCap());
            shape.setStrokeLineJoin(originalShape.getStrokeLineJoin());
            shape.setStrokeMiterLimit(originalShape.getStrokeMiterLimit());
            shape.setStrokeDashOffset(originalShape.getStrokeDashOffset());
            shape.getStrokeDashArray().setAll(originalShape.getStrokeDashArray());
            shape.setVisible(originalShape.isVisible());
            shape.setOpacity(originalShape.getOpacity());
        }

        // 4. 兜底：如果是上面没处理到的神秘节点，就返回原对象（有极小概率报错，但不会再崩溃）
        if (copy == null) {
            return node;
        }

        // 5. 复制通用属性（缩放、旋转、坐标、变换、裁剪）
        copy.setLayoutX(node.getLayoutX());
        copy.setLayoutY(node.getLayoutY());
        copy.setTranslateX(node.getTranslateX());
        copy.setTranslateY(node.getTranslateY());
        copy.setScaleX(node.getScaleX());
        copy.setScaleY(node.getScaleY());
        copy.setRotate(node.getRotate());
        copy.getStyleClass().setAll(node.getStyleClass());
        copy.getTransforms().setAll(node.getTransforms());

        // 如果原节点有裁剪区域（SVG视口裁剪），也要克隆
        if (node.getClip() != null) {
            copy.setClip(cloneNode(node.getClip()));
        }

        return copy;
    }

    /**
     * 直接把一段 {@code path} 的 {@code d} 属性数据渲染成图标（不经过 SVG 文档）。
     *
     * @param pathData SVG 路径数据
     * @param color    图标颜色
     * @param size     图标边长大小（像素）
     * @param isFill   true: 实心图标；false: 线框图标
     * @return JavaFX Node
     */
    public static Node createSvgPath(String pathData, Color color, double size, boolean isFill) {
        if (pathData == null || pathData.isBlank()) {
            return placeholder(size, size);
        }
        SVGPath svgPath = new SVGPath();
        svgPath.setContent(pathData.trim());

        if (isFill) {
            svgPath.setFill(color);
            svgPath.setStroke(null);
        } else {
            svgPath.setFill(null);
            svgPath.setStroke(color);
            svgPath.setStrokeWidth(2); // Lucide 默认线宽
            svgPath.setStrokeLineCap(StrokeLineCap.ROUND);
            svgPath.setStrokeLineJoin(StrokeLineJoin.ROUND);
        }

        // 纯 path 数据默认按 24x24 的 Lucide 视口来缩放
        double viewBoxSize = 24.0;
        double scale = size / viewBoxSize;
        svgPath.setScaleX(scale);
        svgPath.setScaleY(scale);

        Group group = new Group(svgPath);
        group.setTranslateX((size - viewBoxSize * scale) / 2);
        group.setTranslateY((size - viewBoxSize * scale) / 2);
        return group;
    }

    /**
     * 给已经解析出来的图标换色（可以递归处理 Group）。
     *
     * <p>会自动判断每个图形原本是"填充"还是"描边"：只替换实际可见的填充色 / 描边色，
     * {@code fill="none"} 的线框图标不会被错误地填满，也就不再需要 {@code isFill} 参数。
     * 渐变填充会保持原样。</p>
     *
     * @param svgNode 从本工具类创建的 Node
     * @param color   新颜色
     */
    public static void changeColor(Node svgNode, Color color) {
        if (svgNode == null || color == null) {
            return;
        }
        if (svgNode instanceof Shape shape) {
            if (VIEWPORT_MARKER.equals(shape.getUserData())) {
                return;
            }
            Paint fill = shape.getFill();
            if (fill instanceof Color c && c.getOpacity() > 0) {
                shape.setFill(color.deriveColor(0, 1, 1, c.getOpacity()));
            }
            Paint stroke = shape.getStroke();
            if (stroke instanceof Color c && c.getOpacity() > 0) {
                shape.setStroke(color.deriveColor(0, 1, 1, c.getOpacity()));
            }
        } else if (svgNode instanceof Group group) {
            for (Node child : group.getChildren()) {
                changeColor(child, color);
            }
        }
    }

    /**
     * 按旧版 API 的方式强行换色：忽略图形原本的填充 / 描边状态。
     *
     * @param svgNode 从本工具类创建的 Node
     * @param color   新颜色
     * @param isFill  true: 全部当作实心图标；false: 全部当作线框图标
     * @deprecated 建议改用 {@link #changeColor(Node, Color)}，它会自动识别填充 / 描边。
     */
    @Deprecated
    public static void changeColor(Node svgNode, Color color, boolean isFill) {
        if (svgNode == null || color == null) {
            return;
        }
        if (svgNode instanceof Shape shape) {
            if (VIEWPORT_MARKER.equals(shape.getUserData())) {
                return;
            }
            if (isFill) {
                shape.setFill(color);
                shape.setStroke(null);
            } else {
                shape.setFill(null);
                shape.setStroke(color);
            }
        } else if (svgNode instanceof Group group) {
            for (Node child : group.getChildren()) {
                changeColor(child, color, isFill);
            }
        }
    }

    /**
     * 兼容旧版 API：解析 SVG 并强制上色。
     *
     * @param svgFile 本地 SVG 文件
     * @param color   图标颜色
     * @param size    图标边长大小（像素）
     * @param isFill  true: 实心图标；false: 线框图标
     * @return JavaFX Node
     * @deprecated 建议改用 {@link #loadSvg(File, double)} + {@link #changeColor(Node, Color)}。
     */
    @Deprecated
    public static Node loadSvg(File svgFile, Color color, double size, boolean isFill) {
        Node node = loadSvg(svgFile, size);
        changeColor(node, color, isFill);
        return node;
    }

    /**
     * 兼容旧版 API：解析 SVG 文本并强制上色。
     *
     * @param svgContent SVG 源码字符串
     * @param color      图标颜色
     * @param size       图标边长大小（像素）
     * @param isFill     true: 实心图标；false: 线框图标
     * @return JavaFX Node
     * @deprecated 建议改用 {@link #parseSvg(String, double)} + {@link #changeColor(Node, Color)}。
     */
    @Deprecated
    public static Node parseSvgString(String svgContent, Color color, double size, boolean isFill) {
        Node node = parseSvg(svgContent, size);
        changeColor(node, color, isFill);
        return node;
    }

    // ==================================================================
    // 构建入口
    // ==================================================================

    private static Node build(InputSource source, String name, double targetWidth, double targetHeight) throws Exception {
        Document doc = newBuilder().parse(source);
        Element root = doc.getDocumentElement();
        if (root == null) {
            log.warn("{}: 没有根元素", name);
            return placeholder(targetWidth, targetHeight);
        }

        SvgParser parser = new SvgParser();
        if (!"svg".equals(tagNameOf(root))) {
            // 允许直接传入 <path .../> 这样的片段
            log.warn("{}: 根元素 <{}> 不是 <svg>，按图形片段解析", name, tagNameOf(root));
            return parser.parseFragment(root, targetWidth, targetHeight);
        }
        return parser.parseDocument(root, targetWidth, targetHeight);
    }

    private static DocumentBuilder newBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // 允许 DOCTYPE（很多标准 SVG 都带），但禁止加载外部 DTD / 实体，避免 XXE
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (IllegalArgumentException ignored) {
            // 某些实现不支持该属性
        }
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // 某些实现不支持该属性
        }

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override
            public void error(SAXParseException e) {
                // 非致命错误（例如未定义的属性）不打断解析
                log.debug("SVG 解析警告: {}", e.getMessage());
            }

            @Override
            public void fatalError(SAXParseException e) throws SAXParseException {
                throw e;
            }
        });
        return builder;
    }

    private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // 解析器不支持该特性时忽略
        }
    }

    private static String stripBom(String s) {
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }

    private static Node placeholder(double width, double height) {
        double w = width > 0 ? width : 24;
        double h = height > 0 ? height : 24;
        Rectangle region = new Rectangle(w, h);
        region.setFill(Color.TRANSPARENT);
        region.setStroke(null);
        region.setMouseTransparent(true);
        region.setUserData(VIEWPORT_MARKER);
        return region;
    }

    // ==================================================================
    // DOM 辅助方法
    // ==================================================================

    private static String tagNameOf(Element element) {
        String name = element.getLocalName();
        if (name == null || name.isEmpty()) {
            name = element.getNodeName();
        }
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static String attr(Element element, String name) {
        if (element.hasAttribute(name)) {
            return element.getAttribute(name);
        }
        return null;
    }

    private static String attr(Element element, String name, String defaultValue) {
        String value = attr(element, name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /** 读取 {@code href} / {@code xlink:href}。 */
    private static String hrefOf(Element element) {
        String href = element.getAttributeNS(XLINK_NS, "href");
        if (href == null || href.isBlank()) {
            href = attr(element, "href");
        }
        if ((href == null || href.isBlank()) && element.hasAttribute("xlink:href")) {
            href = element.getAttribute("xlink:href");
        }
        return href == null || href.isBlank() ? null : href.trim();
    }

    private static List<Element> childElements(Element parent) {
        List<Element> list = new ArrayList<>();
        for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                list.add((Element) n);
            }
        }
        return list;
    }

    /** 解析 "1, 2, 3" 这样的数字列表。 */
    private static double[] parseNumbers(String text) {
        if (text == null || text.isBlank()) {
            return new double[0];
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        List<Double> values = new ArrayList<>();
        while (matcher.find()) {
            try {
                values.add(Double.parseDouble(matcher.group()));
            } catch (NumberFormatException ignored) {
                // 忽略非法数字
            }
        }
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static double parseNumber(String text, double defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group());
            } catch (NumberFormatException ignored) {
                // ignored
            }
        }
        return defaultValue;
    }

    /**
     * 解析带单位的长度：px / pt / pc / mm / cm / in / em / ex 以及百分比。
     *
     * @param text       属性值
     * @param percentBase 百分比的参照基准（px）
     * @return 像素值；无法解析时返回 {@link Double#NaN}
     */
    private static double parseLength(String text, double percentBase) {
        if (text == null) {
            return Double.NaN;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return Double.NaN;
        }
        Matcher matcher = LENGTH_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return Double.NaN;
        }
        double number;
        try {
            number = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "", "px" -> number;
            case "%" -> number * percentBase / 100.0;
            case "pt" -> number * 96.0 / 72.0;
            case "pc" -> number * 16.0;
            case "mm" -> number * 96.0 / 25.4;
            case "cm" -> number * 96.0 / 2.54;
            case "in" -> number * 96.0;
            case "em" -> number * 16.0;
            case "ex" -> number * 8.0;
            default -> Double.NaN;
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 解析绝对长度（忽略百分比）。用于确定 SVG 的固有尺寸：
     * {@code width="100%"} 这种相对值不能当作固有尺寸使用。
     */
    private static double absoluteLength(String text) {
        if (text == null || text.isBlank() || text.trim().endsWith("%")) {
            return Double.NaN;
        }
        return parseLength(text, 0);
    }

    /** 解析透明度，支持 "0.5" 和 "50%"。 */
    private static double parseOpacity(String text, double defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        String value = text.trim();
        double parsed;
        try {
            if (value.endsWith("%")) {
                parsed = Double.parseDouble(value.substring(0, value.length() - 1).trim()) / 100.0;
            } else {
                parsed = Double.parseDouble(value);
            }
        } catch (NumberFormatException e) {
            return defaultValue;
        }
        return clamp(parsed, 0, 1);
    }

    private static Color parseColor(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Color.web(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ==================================================================
    // 渐变 / 视口 / 变换
    // ==================================================================

    private record ViewBox(double x, double y, double width, double height) {
    }

    /** 对齐方式：fx / fy 分别是水平、垂直方向的对齐系数。 */
    private record AspectRatio(double fx, double fy, boolean uniform, boolean slice) {
    }

    private static AspectRatio parseAspectRatio(String text) {
        AspectRatio defaultRatio = new AspectRatio(0.5, 0.5, true, false);
        if (text == null || text.isBlank()) {
            return defaultRatio;
        }
        String value = text.trim().replaceFirst("(?i)^defer\\s+", "");
        String[] parts = value.split("\\s+");
        String align = parts[0];
        if ("none".equalsIgnoreCase(align)) {
            return new AspectRatio(0, 0, false, false);
        }
        boolean slice = parts.length > 1 && "slice".equalsIgnoreCase(parts[1]);
        double fx = 0.5;
        double fy = 0.5;
        String lower = align.toLowerCase(Locale.ROOT);
        if (lower.startsWith("xmin")) {
            fx = 0;
        } else if (lower.startsWith("xmax")) {
            fx = 1;
        }
        if (lower.contains("ymin")) {
            fy = 0;
        } else if (lower.contains("ymax")) {
            fy = 1;
        }
        return new AspectRatio(fx, fy, true, slice);
    }

    private static Affine fitViewBox(ViewBox viewBox, double outWidth, double outHeight, AspectRatio ratio) {
        double vbWidth = viewBox.width();
        double vbHeight = viewBox.height();
        if (vbWidth <= 0 || vbHeight <= 0 || outWidth <= 0 || outHeight <= 0) {
            return new Affine();
        }
        if (!ratio.uniform()) {
            double sx = outWidth / vbWidth;
            double sy = outHeight / vbHeight;
            return new Affine(sx, 0, -viewBox.x() * sx, 0, sy, -viewBox.y() * sy);
        }
        double scale = ratio.slice()
                ? Math.max(outWidth / vbWidth, outHeight / vbHeight)
                : Math.min(outWidth / vbWidth, outHeight / vbHeight);
        double tx = (outWidth - vbWidth * scale) * ratio.fx() - viewBox.x() * scale;
        double ty = (outHeight - vbHeight * scale) * ratio.fy() - viewBox.y() * scale;
        return new Affine(scale, 0, tx, 0, scale, ty);
    }

    /** 解析 {@code transform} 属性，按 SVG 的顺序（从左到右，先作用的在左边）相乘。 */
    private static Affine parseTransform(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        // 矩阵布局 {a, b, c, d, e, f}：x' = a*x + c*y + e，y' = b*x + d*y + f
        double[] matrix = {1, 0, 0, 1, 0, 0};
        Matcher matcher = TRANSFORM_PATTERN.matcher(text);
        boolean matched = false;
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            double[] args = parseNumbers(matcher.group(2));
            double[] next;
            switch (name) {
                case "matrix" -> {
                    if (args.length < 6) {
                        continue;
                    }
                    next = new double[]{args[0], args[1], args[2], args[3], args[4], args[5]};
                }
                case "translate" -> {
                    if (args.length == 0) {
                        continue;
                    }
                    double tx = args[0];
                    double ty = args.length > 1 ? args[1] : 0;
                    next = new double[]{1, 0, 0, 1, tx, ty};
                }
                case "scale" -> {
                    if (args.length == 0) {
                        continue;
                    }
                    double sx = args[0];
                    double sy = args.length > 1 ? args[1] : sx;
                    next = new double[]{sx, 0, 0, sy, 0, 0};
                }
                case "rotate" -> {
                    if (args.length == 0) {
                        continue;
                    }
                    double angle = Math.toRadians(args[0]);
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    double[] rotation = {cos, sin, -sin, cos, 0, 0};
                    if (args.length >= 3) {
                        double cx = args[1];
                        double cy = args[2];
                        double[] toOrigin = {1, 0, 0, 1, -cx, -cy};
                        double[] back = {1, 0, 0, 1, cx, cy};
                        rotation = multiply(multiply(back, rotation), toOrigin);
                    }
                    next = rotation;
                }
                case "skewx" -> {
                    if (args.length == 0) {
                        continue;
                    }
                    next = new double[]{1, 0, Math.tan(Math.toRadians(args[0])), 1, 0, 0};
                }
                case "skewy" -> {
                    if (args.length == 0) {
                        continue;
                    }
                    next = new double[]{1, Math.tan(Math.toRadians(args[0])), 0, 1, 0, 0};
                }
                default -> {
                    continue;
                }
            }
            matrix = multiply(matrix, next);
            matched = true;
        }
        if (!matched) {
            return null;
        }
        return new Affine(matrix[0], matrix[2], matrix[4], matrix[1], matrix[3], matrix[5]);
    }

    /** 2D 仿射矩阵乘法：result = m * n（先应用 n，再应用 m）。 */
    private static double[] multiply(double[] m, double[] n) {
        return new double[]{
                m[0] * n[0] + m[2] * n[1],
                m[1] * n[0] + m[3] * n[1],
                m[0] * n[2] + m[2] * n[3],
                m[1] * n[2] + m[3] * n[3],
                m[0] * n[4] + m[2] * n[5] + m[4],
                m[1] * n[4] + m[3] * n[5] + m[5]
        };
    }

    // ==================================================================
    // 解析器
    // ==================================================================

    /** 单次解析的上下文。 */
    private static final class SvgParser {

        private final Map<String, Element> ids = new HashMap<>();
        private final List<CssRule> cssRules = new ArrayList<>();
        private final Set<String> warned = new HashSet<>();
        private final Set<String> useStack = new HashSet<>();

        /** 当前视口的宽高（viewBox 尺寸），用于解析百分比长度。 */
        private double viewportWidth = DEFAULT_WIDTH;
        private double viewportHeight = DEFAULT_HEIGHT;

        // ------------------------- 文档入口 -------------------------

        Node parseDocument(Element root, double targetWidth, double targetHeight) {
            collectIds(root);
            collectCss(root);

            ViewBox viewBox = parseViewBox(root);
            double intrinsicWidth = absoluteLength(attr(root, "width"));
            double intrinsicHeight = absoluteLength(attr(root, "height"));
            if (Double.isNaN(intrinsicWidth) || intrinsicWidth <= 0) {
                intrinsicWidth = viewBox != null ? viewBox.width() : DEFAULT_WIDTH;
            }
            if (Double.isNaN(intrinsicHeight) || intrinsicHeight <= 0) {
                intrinsicHeight = viewBox != null ? viewBox.height() : DEFAULT_HEIGHT;
            }
            if (viewBox == null) {
                viewBox = new ViewBox(0, 0, intrinsicWidth, intrinsicHeight);
            }

            double outWidth = targetWidth > 0 ? targetWidth : intrinsicWidth;
            double outHeight = targetHeight > 0 ? targetHeight : intrinsicHeight;
            AspectRatio ratio = parseAspectRatio(attr(root, "preserveAspectRatio"));

            double oldWidth = viewportWidth;
            double oldHeight = viewportHeight;
            viewportWidth = viewBox.width();
            viewportHeight = viewBox.height();

            Group content = new Group();
            Style rootStyle = computeStyle(root, Style.initial());
            try {
                if (!isHidden(rootStyle)) {
                    renderChildren(root, rootStyle, content);
                }
            } finally {
                viewportWidth = oldWidth;
                viewportHeight = oldHeight;
            }

            if (content.getChildren().isEmpty()) {
                log.warn("SVG 中没有可渲染的图形");
                return placeholder(outWidth, outHeight);
            }

            // JavaFX 的变换列表按"后面的先作用"组合（p = t1 * t2 * p），
            // 因此列表顺序为 [root transform, viewBox 映射]，等价于先做 viewBox 映射再套 root 的 transform。
            Affine rootTransform = parseTransform(attr(root, "transform"));
            if (rootTransform != null) {
                content.getTransforms().add(rootTransform);
            }
            content.getTransforms().add(fitViewBox(viewBox, outWidth, outHeight, ratio));
            if (rootStyle.opacity < 1) {
                content.setOpacity(rootStyle.opacity);
            }

            // 用一个透明的视口矩形垫底，保证 layoutBounds 恰好是 (0, 0, outWidth, outHeight)，
            // 这样不同图标放进同一个容器时能按同一套网格对齐。
            Rectangle viewport = new Rectangle(outWidth, outHeight);
            viewport.setFill(Color.TRANSPARENT);
            viewport.setStroke(null);
            viewport.setMouseTransparent(true);
            viewport.setUserData(VIEWPORT_MARKER);

            Group result = new Group(viewport, content);
            boolean overflowVisible = "visible".equalsIgnoreCase(attr(root, "overflow", "hidden"));
            if (!overflowVisible) {
                // SVG 根元素的 overflow 默认是 hidden：超出视口的部分应当被裁剪
                Rectangle clip = new Rectangle(outWidth, outHeight);
                result.setClip(clip);
            }
            return result;
        }

        /** 根元素不是 svg 时，把整个文档当作单个图形片段处理。 */
        Node parseFragment(Element root, double targetWidth, double targetHeight) {
            collectIds(root);
            collectCss(root);

            Group content = new Group();
            renderElement(root, Style.initial(), content);
            if (content.getChildren().isEmpty()) {
                return placeholder(targetWidth, targetHeight);
            }

            Bounds bounds = content.getLayoutBounds();
            double boundsWidth = bounds.getWidth();
            double boundsHeight = bounds.getHeight();
            if (boundsWidth <= 0 || boundsHeight <= 0) {
                return new Group(content);
            }
            boolean fit = targetWidth > 0 || targetHeight > 0;
            double outWidth = targetWidth > 0 ? targetWidth : boundsWidth;
            double outHeight = targetHeight > 0 ? targetHeight : boundsHeight;
            double scale = fit ? Math.min(outWidth / boundsWidth, outHeight / boundsHeight) : 1;
            double tx = (outWidth - boundsWidth * scale) / 2 - bounds.getMinX() * scale;
            double ty = (outHeight - boundsHeight * scale) / 2 - bounds.getMinY() * scale;
            content.getTransforms().add(new Affine(scale, 0, tx, 0, scale, ty));
            return new Group(content);
        }

        private void collectIds(Element root) {
            addId(root);
            NodeList all = root.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                if (all.item(i) instanceof Element element) {
                    addId(element);
                }
            }
        }

        private void addId(Element element) {
            String id = attr(element, "id");
            if (id != null && !id.isBlank()) {
                ids.putIfAbsent(id.trim(), element);
            }
        }

        private void collectCss(Element root) {
            NodeList styles = root.getElementsByTagName("style");
            for (int i = 0; i < styles.getLength(); i++) {
                if (styles.item(i) instanceof Element style) {
                    String type = attr(style, "type", "text/css");
                    if (type.contains("css")) {
                        parseCss(style.getTextContent());
                    }
                }
            }
        }

        // ------------------------- CSS -------------------------

        private void parseCss(String css) {
            if (css == null || css.isBlank()) {
                return;
            }
            String text = css.replaceAll("(?s)/\\*.*?\\*/", "");
            int index = 0;
            int order = 0;
            while (true) {
                int open = text.indexOf('{', index);
                if (open < 0) {
                    break;
                }
                int close = text.indexOf('}', open);
                if (close < 0) {
                    break;
                }
                String selectorText = text.substring(index, open).trim();
                Map<String, String> declarations = parseDeclarations(text.substring(open + 1, close));
                index = close + 1;
                if (declarations.isEmpty() || selectorText.isEmpty() || selectorText.startsWith("@")) {
                    continue;
                }
                for (String selector : selectorText.split(",")) {
                    CssRule rule = CssRule.of(selector.trim(), declarations, order++);
                    if (rule != null) {
                        cssRules.add(rule);
                    }
                }
            }
        }

        private Map<String, String> parseDeclarations(String body) {
            Map<String, String> declarations = new LinkedHashMap<>();
            for (String part : body.split(";")) {
                int colon = part.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String property = part.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = part.substring(colon + 1).trim()
                        .replaceFirst("(?i)\\s*!important\\s*$", "");
                if (!property.isEmpty() && !value.isEmpty()) {
                    declarations.put(property, value);
                }
            }
            return declarations;
        }

        private void applyCss(Element element, Style style) {
            if (cssRules.isEmpty()) {
                return;
            }
            List<CssRule> matched = new ArrayList<>();
            for (CssRule rule : cssRules) {
                if (rule.matches(element)) {
                    matched.add(rule);
                }
            }
            matched.sort((a, b) -> {
                int bySpecificity = Integer.compare(a.specificity, b.specificity);
                return bySpecificity != 0 ? bySpecificity : Integer.compare(a.order, b.order);
            });
            for (CssRule rule : matched) {
                for (Map.Entry<String, String> entry : rule.declarations.entrySet()) {
                    applyProperty(entry.getKey(), entry.getValue(), style);
                }
            }
        }

        // ------------------------- 样式计算 -------------------------

        private Style computeStyle(Element element, Style parent) {
            Style style = parent.copy();
            // opacity / display 不继承，其余属性（fill、stroke、font 等）继承
            style.opacity = 1;
            style.display = "inline";

            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                if (attributes.item(i) instanceof Attr attribute) {
                    applyProperty(attribute.getName(), attribute.getValue(), style);
                }
            }
            applyCss(element, style);
            String inlineStyle = attr(element, "style");
            if (inlineStyle != null) {
                for (Map.Entry<String, String> entry : parseDeclarations(inlineStyle).entrySet()) {
                    applyProperty(entry.getKey(), entry.getValue(), style);
                }
            }
            return style;
        }

        private void applyProperty(String rawName, String rawValue, Style style) {
            if (rawName == null || rawValue == null) {
                return;
            }
            String name = rawName.toLowerCase(Locale.ROOT);
            String value = rawValue.trim();
            if (value.isEmpty()) {
                return;
            }
            switch (name) {
                case "fill" -> style.fill = value;
                case "stroke" -> style.stroke = value;
                case "fill-opacity" -> style.fillOpacity = parseOpacity(value, style.fillOpacity);
                case "stroke-opacity" -> style.strokeOpacity = parseOpacity(value, style.strokeOpacity);
                case "opacity" -> style.opacity = parseOpacity(value, 1);
                case "stroke-width" -> {
                    double width = parseLength(value, Math.hypot(viewportWidth, viewportHeight) / Math.sqrt(2));
                    if (!Double.isNaN(width)) {
                        style.strokeWidth = Math.max(0, width);
                    }
                }
                case "stroke-linecap" -> style.lineCap = value.toLowerCase(Locale.ROOT);
                case "stroke-linejoin" -> style.lineJoin = value.toLowerCase(Locale.ROOT);
                case "stroke-miterlimit" -> style.miterLimit = parseNumber(value, style.miterLimit);
                case "stroke-dasharray" -> style.dashArray =
                        "none".equalsIgnoreCase(value) ? null : value;
                case "stroke-dashoffset" -> {
                    double offset = parseLength(value, 1);
                    if (!Double.isNaN(offset)) {
                        style.dashOffset = offset;
                    }
                }
                case "fill-rule" -> style.fillRule = value.toLowerCase(Locale.ROOT);
                case "display" -> style.display = value.toLowerCase(Locale.ROOT);
                case "visibility" -> style.visibility = value.toLowerCase(Locale.ROOT);
                case "color" -> {
                    if ("currentColor".equalsIgnoreCase(value)) {
                        // currentColor 指向自身会死循环，忽略
                    } else {
                        Color color = parseColor(value);
                        if (color != null) {
                            style.color = color;
                        }
                    }
                }
                case "font-family" -> style.fontFamily = value;
                case "font-size" -> style.fontSize = value;
                case "font-weight" -> style.fontWeight = value;
                case "font-style" -> style.fontStyle = value;
                case "text-anchor" -> style.textAnchor = value.toLowerCase(Locale.ROOT);
                default -> {
                    // 不支持的属性直接忽略
                }
            }
        }

        /** display:none / visibility:hidden 的元素及其子树都不渲染。 */
        private boolean isHidden(Style style) {
            return "none".equals(style.display)
                    || "hidden".equals(style.visibility)
                    || "collapse".equals(style.visibility)
                    || style.opacity <= 0;
        }

        // ------------------------- 元素分发 -------------------------

        private void renderChildren(Element parent, Style parentStyle, Group target) {
            for (Element child : childElements(parent)) {
                renderElement(child, parentStyle, target);
            }
        }

        private void renderElement(Element element, Style parentStyle, Group target) {
            String tag = tagNameOf(element);
            switch (tag) {
                case "g", "a" -> renderGroup(element, parentStyle, target);
                case "switch" -> {
                    for (Element child : childElements(element)) {
                        renderElement(child, parentStyle, target);
                        break;
                    }
                }
                case "svg" -> renderNestedSvg(element, parentStyle, target);
                case "use" -> renderUse(element, parentStyle, target);
                case "path" -> renderPath(element, parentStyle, target);
                case "rect" -> renderRect(element, parentStyle, target);
                case "circle" -> renderCircle(element, parentStyle, target);
                case "ellipse" -> renderEllipse(element, parentStyle, target);
                case "line" -> renderLine(element, parentStyle, target);
                case "polyline" -> renderPolyline(element, parentStyle, target);
                case "polygon" -> renderPolygon(element, parentStyle, target);
                case "text" -> renderText(element, parentStyle, target);
                case "defs", "symbol", "style", "title", "desc", "metadata",
                     "linearGradient", "radialGradient", "stop", "pattern",
                     "clipPath", "mask", "marker", "filter", "script", "foreignObject" -> {
                    // 这些元素不直接渲染
                }
                case "image" -> warnOnce("image", "<image> 元素暂不支持，已忽略");
                default -> warnOnce("tag-" + tag, "不支持的 SVG 元素 <" + tag + ">，已忽略");
            }
        }

        private void renderGroup(Element element, Style parentStyle, Group target) {
            Style style = computeStyle(element, parentStyle);
            if (isHidden(style)) {
                return;
            }
            Group group = new Group();
            applyNodeTransform(element, group);
            if (style.opacity < 1) {
                group.setOpacity(style.opacity);
            }
            renderChildren(element, style, group);
            if (!group.getChildren().isEmpty()) {
                target.getChildren().add(group);
            }
        }

        private void renderNestedSvg(Element element, Style parentStyle, Group target) {
            Style style = computeStyle(element, parentStyle);
            if (isHidden(style)) {
                return;
            }
            double x = length(element, "x", 0);
            double y = length(element, "y", 0);
            double width = length(element, "width", viewportWidth);
            double height = length(element, "height", viewportHeight);
            if (width <= 0) {
                width = viewportWidth;
            }
            if (height <= 0) {
                height = viewportHeight;
            }

            Group group = new Group();
            applyNodeTransform(element, group);
            group.getTransforms().add(new Translate(x, y));
            if (style.opacity < 1) {
                group.setOpacity(style.opacity);
            }

            ViewBox viewBox = parseViewBox(element);
            if (viewBox == null) {
                renderChildren(element, style, group);
            } else {
                Group inner = new Group();
                inner.getTransforms().add(fitViewBox(viewBox, width, height,
                        parseAspectRatio(attr(element, "preserveAspectRatio"))));
                double oldWidth = viewportWidth;
                double oldHeight = viewportHeight;
                viewportWidth = viewBox.width();
                viewportHeight = viewBox.height();
                try {
                    renderChildren(element, style, inner);
                } finally {
                    viewportWidth = oldWidth;
                    viewportHeight = oldHeight;
                }
                group.getChildren().add(inner);
            }
            if (!group.getChildren().isEmpty()) {
                target.getChildren().add(group);
            }
        }

        private void renderUse(Element element, Style parentStyle, Group target) {
            String href = hrefOf(element);
            if (href == null || !href.startsWith("#")) {
                warnOnce("use-href", "<use> 只支持引用本文档内的元素（#id）: " + href);
                return;
            }
            String id = href.substring(1);
            Element referenced = ids.get(id);
            if (referenced == null) {
                warnOnce("use-" + id, "<use> 引用的元素不存在: #" + id);
                return;
            }
            if (!useStack.add(id)) {
                warnOnce("use-cycle-" + id, "<use> 出现循环引用: #" + id);
                return;
            }
            try {
                Style style = computeStyle(element, parentStyle);
                if (isHidden(style)) {
                    return;
                }
                double x = length(element, "x", 0);
                double y = length(element, "y", 0);

                Group group = new Group();
                applyNodeTransform(element, group);
                group.getTransforms().add(new Translate(x, y));
                if (style.opacity < 1) {
                    group.setOpacity(style.opacity);
                }

                String tag = tagNameOf(referenced);
                if ("symbol".equals(tag) || "svg".equals(tag)) {
                    renderSymbol(referenced, style, group, element);
                } else {
                    renderElement(referenced, style, group);
                }
                if (!group.getChildren().isEmpty()) {
                    target.getChildren().add(group);
                }
            } finally {
                useStack.remove(id);
            }
        }

        /** 渲染 {@code <use>} 引用的 {@code <symbol>} / {@code <svg>}。 */
        private void renderSymbol(Element symbol, Style parentStyle, Group target, Element useElement) {
            Style style = computeStyle(symbol, parentStyle);
            if (isHidden(style)) {
                return;
            }
            double width = length(useElement, "width", Double.NaN);
            double height = length(useElement, "height", Double.NaN);
            ViewBox viewBox = parseViewBox(symbol);
            if (viewBox == null) {
                renderChildren(symbol, style, target);
                return;
            }
            if (Double.isNaN(width) || width <= 0) {
                width = viewBox.width();
            }
            if (Double.isNaN(height) || height <= 0) {
                height = viewBox.height();
            }
            Group inner = new Group();
            inner.getTransforms().add(fitViewBox(viewBox, width, height,
                    parseAspectRatio(attr(symbol, "preserveAspectRatio"))));
            double oldWidth = viewportWidth;
            double oldHeight = viewportHeight;
            viewportWidth = viewBox.width();
            viewportHeight = viewBox.height();
            try {
                renderChildren(symbol, style, inner);
            } finally {
                viewportWidth = oldWidth;
                viewportHeight = oldHeight;
            }
            target.getChildren().add(inner);
        }

        // ------------------------- 图形元素 -------------------------

        private void renderPath(Element element, Style parentStyle, Group target) {
            Style style = computeStyle(element, parentStyle);
            if (isHidden(style)) {
                return;
            }
            String data = attr(element, "d");
            if (data == null || data.isBlank()) {
                return;
            }
            SVGPath path = new SVGPath();
            path.setContent(data.trim());
            addShape(element, path, style, target);
        }

        private void renderRect(Element element, Style parentStyle, Group target) {
            Style style = computeStyle(element, parentStyle);
            if (isHidden(style)) {
                return;
            }
            double x = length(element, "x", 0);
            double y = length(element, "y", 0);
            double width = length(element, "width", 0);
            double height = length(element, "height", 0);
            if (width <= 0 || height <= 0) {
                return;
            }
            double rx = attr(element, "rx") != null ? length(element, "rx", 0) : Double.NaN;
            double ry = attr(element, "ry") != null ? length(element, "ry", 0) : Double.NaN;
            if (Double.isNaN(rx) && Double.isNaN(ry)) {
                rx = 0;
                ry = 0;
            } else if (Double.isNaN(rx)) {
                rx = ry;
            } else if (Double.isNaN(ry)) {
                ry = rx;
            }
            rx = clamp(rx, 0, width / 2);
            ry = clamp(ry, 0, height / 2);

            Rectangle rectangle = new Rectangle(x, y, width, height);
            rectangle.setArcWidth(rx * 2);
            rectangle.setArcHeight(ry * 2);
            addShape(element, rectangle, style, target);
        }

        private void renderCircle(Element element, Style parentStyle, Group target) {
            Style style = computeStyle(element, parentStyle);
            if (isHidden(style)) {
                return;
            }
            double cx = length(element, "cx", 0);
            double cy = length(element, "cy", 0);
            double r = length(element, "r", 0);
            if (r <= 0) {
                return;
            }
            addShape(element, new Circle(cx, cy, r), style, target);
        }

        private void renderEllipse(Element element, Style parentStyle, Group target) {
            Style style = computeStyle(element, parentStyle);
            if (isHidden(style)) {
                return;
            }
            double cx = length(element, "cx", 0);
            double cy = length(element, "cy", 0);
            double rx = length(element, "rx", 0);
            double ry = length(element, "ry", 0);
            if (rx <= 0 || ry <= 0) {
                return;
            }
            addShape(element, new Ellipse(cx, cy, rx, ry), style, target);
        }

        private void renderLine(Element element, Style parentStyle, Group target) {
            Style style = computeStyle(element, parentStyle);
            if (isHidden(style)) {
                return;
            }
            double x1 = length(element, "x1", 0);
            double y1 = length(element, "y1", 0);
            double x2 = length(element, "x2", 0);
            double y2 = length(element, "y2", 0);
            addShape(element, new Line(x1, y1, x2, y2), style, target);
        }

        private void renderPolyline(Element element, Style parentStyle, Group target) {
            Style style = computeStyle(element, parentStyle);
            if (isHidden(style)) {
                return;
            }
            double[] points = parseNumbers(attr(element, "points"));
            if (points.length < 4) {
                return;
            }
            addShape(element, new Polyline(points), style, target);
        }

        private void renderPolygon(Element element, Style parentStyle, Group target) {
            Style style = computeStyle(element, parentStyle);
            if (isHidden(style)) {
                return;
            }
            double[] points = parseNumbers(attr(element, "points"));
            if (points.length < 6) {
                return;
            }
            addShape(element, new Polygon(points), style, target);
        }

        private void renderText(Element element, Style parentStyle, Group target) {
            Style style = computeStyle(element, parentStyle);
            if (isHidden(style)) {
                return;
            }
            String content = element.getTextContent();
            if (content == null || content.isBlank()) {
                return;
            }
            double x = length(element, "x", 0);
            double y = length(element, "y", 0);
            double dx = length(element, "dx", 0);
            double dy = length(element, "dy", 0);

            Text text = new Text(content.trim());
            text.setFont(resolveFont(style));
            text.setTextOrigin(VPos.BASELINE);

            double offset = 0;
            switch (style.textAnchor) {
                case "middle" -> offset = -text.getLayoutBounds().getWidth() / 2;
                case "end" -> offset = -text.getLayoutBounds().getWidth();
                default -> {
                    // start
                }
            }
            text.setX(x + dx + offset);
            text.setY(y + dy);
            addShape(element, text, style, target);
        }

        private Font resolveFont(Style style) {
            double size = parseLength(style.fontSize, 16);
            if (Double.isNaN(size) || size <= 0) {
                size = 16;
            }
            FontWeight weight = resolveFontWeight(style.fontWeight);
            FontPosture posture = switch (style.fontStyle == null ? "normal" : style.fontStyle) {
                case "italic", "oblique" -> FontPosture.ITALIC;
                default -> FontPosture.REGULAR;
            };
            String family = resolveFontFamily(style.fontFamily);
            if (family == null) {
                try {
                    family = Font.getDefault().getFamily();
                } catch (RuntimeException e) {
                    family = "System";
                }
            }
            return Font.font(family, weight, posture, size);
        }

        private FontWeight resolveFontWeight(String rawWeight) {
            if (rawWeight == null || rawWeight.isBlank()) {
                return FontWeight.NORMAL;
            }
            String weight = rawWeight.trim().toLowerCase(Locale.ROOT);
            return switch (weight) {
                case "bold", "bolder", "600", "700", "800", "900" -> FontWeight.BOLD;
                case "lighter", "100", "200", "300" -> FontWeight.LIGHT;
                default -> FontWeight.NORMAL;
            };
        }

        private String resolveFontFamily(String rawFamily) {
            if (rawFamily == null || rawFamily.isBlank()) {
                return null;
            }
            String family = rawFamily.split(",")[0].trim().replace("\"", "").replace("'", "");
            return switch (family.toLowerCase(Locale.ROOT)) {
                case "sans-serif" -> "System";
                case "serif" -> "Serif";
                case "monospace" -> "Monospaced";
                case "cursive", "fantasy" -> null;
                default -> family.isEmpty() ? null : family;
            };
        }

        // ------------------------- 视图属性 -------------------------

        private void addShape(Element element, Shape shape, Style style, Group target) {
            applyShapeStyle(shape, style);
            applyNodeTransform(element, shape);
            if (style.opacity < 1) {
                shape.setOpacity(style.opacity);
            }
            target.getChildren().add(shape);
        }

        private void applyNodeTransform(Element element, Node node) {
            Affine transform = parseTransform(attr(element, "transform"));
            if (transform != null) {
                node.getTransforms().add(transform);
            }
        }

        private void applyShapeStyle(Shape shape, Style style) {
            shape.setFill(resolvePaint(style.fill, style.fillOpacity, style));
            shape.setStroke(resolvePaint(style.stroke, style.strokeOpacity, style));
            shape.setStrokeWidth(style.strokeWidth);
            shape.setStrokeMiterLimit(style.miterLimit);
            shape.setStrokeLineCap(switch (style.lineCap) {
                case "round" -> StrokeLineCap.ROUND;
                case "square" -> StrokeLineCap.SQUARE;
                default -> StrokeLineCap.BUTT;
            });
            shape.setStrokeLineJoin(switch (style.lineJoin) {
                case "round" -> StrokeLineJoin.ROUND;
                case "bevel" -> StrokeLineJoin.BEVEL;
                default -> StrokeLineJoin.MITER;
            });
            shape.setStrokeDashOffset(style.dashOffset);
            shape.getStrokeDashArray().clear();
            if (style.dashArray != null) {
                double[] dashes = parseNumbers(style.dashArray);
                boolean valid = dashes.length > 0;
                double sum = 0;
                for (double dash : dashes) {
                    if (dash < 0) {
                        valid = false;
                        break;
                    }
                    sum += dash;
                }
                if (valid && sum > 0) {
                    // SVG 规定奇数个值时重复一遍，正好对应 JavaFX 的 偶数长度 要求
                    int length = dashes.length % 2 == 0 ? dashes.length : dashes.length * 2;
                    Double[] boxed = new Double[length];
                    for (int i = 0; i < length; i++) {
                        boxed[i] = dashes[i % dashes.length];
                    }
                    shape.getStrokeDashArray().setAll(boxed);
                }
            }
            if (shape instanceof SVGPath path) {
                path.setFillRule("evenodd".equals(style.fillRule) ? FillRule.EVEN_ODD : FillRule.NON_ZERO);
            }
        }

        private Paint resolvePaint(String value, double opacity, Style style) {
            if (value == null) {
                return null;
            }
            String text = value.trim();
            if (text.isEmpty() || "none".equalsIgnoreCase(text)) {
                return null;
            }
            if ("currentColor".equalsIgnoreCase(text)) {
                return withAlpha(style.color, opacity);
            }
            if (text.startsWith("url(")) {
                int end = text.indexOf(')');
                if (end > 4) {
                    String id = text.substring(4, end).trim().replace("\"", "").replace("'", "");
                    if (id.startsWith("#")) {
                        id = id.substring(1);
                    }
                    return gradientPaint(id, opacity, style);
                }
                return null;
            }
            Color color = parseColor(text);
            return color == null ? null : withAlpha(color, opacity);
        }

        private Color withAlpha(Color color, double opacity) {
            double alpha = clamp(color.getOpacity() * opacity, 0, 1);
            if (alpha >= 1) {
                return color;
            }
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }

        private Paint gradientPaint(String id, double opacity, Style style) {
            Element gradient = ids.get(id);
            if (gradient == null) {
                warnOnce("gradient-" + id, "找不到渐变定义: #" + id);
                return null;
            }
            String tag = tagNameOf(gradient);
            boolean linear = "linearGradient".equals(tag);
            if (!linear && !"radialGradient".equals(tag)) {
                warnOnce("gradient-tag-" + id, "不支持的填充类型 <" + tag + ">，已忽略");
                return null;
            }
            List<Stop> stops = collectStops(gradient, new HashSet<>(), style);
            if (stops.isEmpty()) {
                return null;
            }
            List<Stop> tinted = new ArrayList<>(stops.size());
            for (Stop stop : stops) {
                tinted.add(new Stop(stop.getOffset(), withAlpha(stop.getColor(), opacity)));
            }
            if (tinted.isEmpty()) {
                return null;
            }

            boolean userSpace = "userSpaceOnUse".equals(attr(gradient, "gradientUnits", "objectBoundingBox"));
            CycleMethod cycleMethod = switch (attr(gradient, "spreadMethod", "pad").toLowerCase(Locale.ROOT)) {
                case "reflect" -> CycleMethod.REFLECT;
                case "repeat" -> CycleMethod.REPEAT;
                default -> CycleMethod.NO_CYCLE;
            };

            if (linear) {
                double x1 = gradientCoordinate(attr(gradient, "x1"), userSpace, true, 0);
                double y1 = gradientCoordinate(attr(gradient, "y1"), userSpace, false, 0);
                double x2 = gradientCoordinate(attr(gradient, "x2"), userSpace, true, 1);
                double y2 = gradientCoordinate(attr(gradient, "y2"), userSpace, false, 0);
                return new LinearGradient(x1, y1, x2, y2, !userSpace, cycleMethod, tinted);
            }

            double cx = gradientCoordinate(attr(gradient, "cx"), userSpace, true, 0.5);
            double cy = gradientCoordinate(attr(gradient, "cy"), userSpace, false, 0.5);
            double radius = gradientCoordinate(attr(gradient, "r"), userSpace, true, 0.5);
            double fx = gradientCoordinate(attr(gradient, "fx"), userSpace, true, 0.5);
            double fy = gradientCoordinate(attr(gradient, "fy"), userSpace, false, 0.5);
            if (radius <= 0) {
                return null;
            }
            double focusAngle = Math.toDegrees(Math.atan2(fy - cy, fx - cx));
            double focusDistance = Math.hypot(fx - cx, fy - cy) / radius;
            return new RadialGradient(focusAngle, focusDistance, cx, cy, radius,
                    !userSpace, cycleMethod, tinted);
        }

        private double gradientCoordinate(String raw, boolean userSpace, boolean xAxis, double defaultFraction) {
            double base = xAxis ? viewportWidth : viewportHeight;
            if (raw == null || raw.isBlank()) {
                return userSpace ? defaultFraction * base : defaultFraction;
            }
            String value = raw.trim();
            if (value.endsWith("%")) {
                double fraction = parseNumber(value, defaultFraction * 100) / 100.0;
                return userSpace ? fraction * base : fraction;
            }
            double parsed = parseLength(value, base);
            if (Double.isNaN(parsed)) {
                return userSpace ? defaultFraction * base : defaultFraction;
            }
            return parsed;
        }

        private List<Stop> collectStops(Element gradient, Set<String> visited, Style style) {
            String id = attr(gradient, "id");
            if (id != null && !id.isBlank() && !visited.add(id)) {
                return List.of();
            }
            List<Stop> stops = new ArrayList<>();
            for (Element child : childElements(gradient)) {
                if (!"stop".equals(tagNameOf(child))) {
                    continue;
                }
                double offset = clamp(parseOpacity(stopProperty(child, "offset"), 0), 0, 1);
                String colorText = stopProperty(child, "stop-color");
                Color color = "currentColor".equalsIgnoreCase(colorText == null ? "" : colorText.trim())
                        ? style.color
                        : parseColor(colorText == null ? "black" : colorText);
                if (color == null) {
                    color = Color.BLACK;
                }
                double stopOpacity = parseOpacity(stopProperty(child, "stop-opacity"), 1);
                stops.add(new Stop(offset, withAlpha(color, stopOpacity)));
            }
            if (stops.isEmpty()) {
                String href = hrefOf(gradient);
                if (href != null && href.startsWith("#")) {
                    Element referenced = ids.get(href.substring(1));
                    if (referenced != null) {
                        stops.addAll(collectStops(referenced, visited, style));
                    }
                }
            }
            return stops;
        }

        private String stopProperty(Element stop, String name) {
            String inlineStyle = attr(stop, "style");
            if (inlineStyle != null) {
                Map<String, String> declarations = parseDeclarations(inlineStyle);
                String value = declarations.get(name);
                if (value != null) {
                    return value;
                }
            }
            return attr(stop, name);
        }

        // ------------------------- 其它 -------------------------

        private double length(Element element, String name, double defaultValue) {
            String raw = attr(element, name);
            if (raw == null || raw.isBlank()) {
                return defaultValue;
            }
            double value = parseLength(raw, "width".equals(name) || "x".equals(name) ? viewportWidth : viewportHeight);
            return Double.isNaN(value) ? defaultValue : value;
        }

        private ViewBox parseViewBox(Element element) {
            double[] numbers = parseNumbers(attr(element, "viewBox"));
            if (numbers.length < 4 || numbers[2] <= 0 || numbers[3] <= 0) {
                return null;
            }
            return new ViewBox(numbers[0], numbers[1], numbers[2], numbers[3]);
        }

        private void warnOnce(String key, String message) {
            if (warned.add(key)) {
                log.warn(message);
            }
        }
    }

    // ==================================================================
    // 样式
    // ==================================================================

    /** 元素的可继承样式 + 自身样式。 */
    private static final class Style {

        String fill = "black";
        String stroke = "none";
        double fillOpacity = 1;
        double strokeOpacity = 1;
        double strokeWidth = 1;
        String lineCap = "butt";
        String lineJoin = "miter";
        double miterLimit = 4;
        String dashArray = null;
        double dashOffset = 0;
        String fillRule = "nonzero";
        String display = "inline";
        String visibility = "visible";
        double opacity = 1;
        Color color = Color.BLACK;
        String fontFamily = null;
        String fontSize = "16";
        String fontWeight = "normal";
        String fontStyle = "normal";
        String textAnchor = "start";

        static Style initial() {
            return new Style();
        }

        Style copy() {
            Style copy = new Style();
            copy.fill = fill;
            copy.stroke = stroke;
            copy.fillOpacity = fillOpacity;
            copy.strokeOpacity = strokeOpacity;
            copy.strokeWidth = strokeWidth;
            copy.lineCap = lineCap;
            copy.lineJoin = lineJoin;
            copy.miterLimit = miterLimit;
            copy.dashArray = dashArray;
            copy.dashOffset = dashOffset;
            copy.fillRule = fillRule;
            copy.display = display;
            copy.visibility = visibility;
            copy.opacity = opacity;
            copy.color = color;
            copy.fontFamily = fontFamily;
            copy.fontSize = fontSize;
            copy.fontWeight = fontWeight;
            copy.fontStyle = fontStyle;
            copy.textAnchor = textAnchor;
            return copy;
        }
    }

    /** 一条简单的 CSS 规则。 */
    private static final class CssRule {

        final String tag;
        final String id;
        final List<String> classes;
        final Map<String, String> declarations;
        final int specificity;
        final int order;

        private CssRule(String tag, String id, List<String> classes,
                        Map<String, String> declarations, int specificity, int order) {
            this.tag = tag;
            this.id = id;
            this.classes = classes;
            this.declarations = declarations;
            this.specificity = specificity;
            this.order = order;
        }

        static CssRule of(String selector, Map<String, String> declarations, int order) {
            if (selector == null || selector.isEmpty()) {
                return null;
            }
            // 只支持简单选择符：tag、.class、#id 及其组合；后代/子选择器直接跳过
            Matcher matcher = SELECTOR_PATTERN.matcher(selector);
            if (!matcher.matches()) {
                return null;
            }
            String tag = matcher.group(1);
            String rest = matcher.group(2);
            String id = null;
            List<String> classes = new ArrayList<>();
            Matcher token = Pattern.compile("[.#][\\w-]+").matcher(rest);
            while (token.find()) {
                String value = token.group();
                if (value.startsWith("#")) {
                    id = value.substring(1);
                } else {
                    classes.add(value.substring(1));
                }
            }
            int specificity = (id != null ? 100 : 0) + classes.size() * 10
                    + (tag != null && !"*".equals(tag) ? 1 : 0);
            return new CssRule(tag, id, classes, declarations, specificity, order);
        }

        boolean matches(Element element) {
            if (tag != null && !"*".equals(tag) && !tag.equals(tagNameOf(element))) {
                return false;
            }
            if (id != null && !id.equals(attr(element, "id"))) {
                return false;
            }
            if (!classes.isEmpty()) {
                String classAttribute = attr(element, "class");
                if (classAttribute == null) {
                    return false;
                }
                List<String> present = Arrays.asList(classAttribute.trim().split("\\s+"));
                for (String cssClass : classes) {
                    if (!present.contains(cssClass)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
