package com.convict.geetest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: Convict.Yellow
 * @date: 2020/8/13 11:12
 * @description: selenium识别极验滑动验证码(多图合并)，下载验证码图片，并还原为正常缺口图、正常完整图。
 * 虎嗅网移动端登录为例：https://m.huxiu.com/login/
 *
 * 1.下载验证码图片
 * 2.分割所有验证码图片，并还原成正常验证码图片
 * 3.根据移动距离，模拟真人鼠标移动
 */
public class GeetestMultipartImage {

    // 所有图片存储路径
    private static final String BASE_PATH = "D:/tmpImgDir/";
    // 完整图前缀
    private static final String FULL_IMAGE_NAME = "full";
    // 缺口图前缀
    private static final String CUT_IMAGE_NAME = "cut";
    // 存储 2 * 26 = 52 个图的坐标信息，-157px -58px;
    private static int[][] locations = new int[52][2];

    private static ChromeDriver driver;
    private static WebDriverWait wait;

    static {
        System.getProperties().setProperty("webdriver.chrome.driver", "C:\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        driver = new ChromeDriver(options);

        wait = new WebDriverWait(driver, 10);
        File baseDir = new File(BASE_PATH);
        if (!baseDir.exists()) baseDir.mkdir();
    }

    public static void main(String[] args) {
        int showCount = 3;
        try {
            for (int i = 0; i < showCount; i++) {
                boolean recognize;
                do {
                    driver.get("https://m.huxiu.com/login");
                    int distance = calMoveDistance();

                    waitBy(By.xpath("//div[@class='gt_slider_knob gt_show']"));
                    Actions actions = new Actions(driver);
                    WebElement moveBtnEle = driver.findElement(By.xpath("//div[@class='gt_slider_knob gt_show']"));
                    actions.moveToElement(moveBtnEle);
                    actions.clickAndHold(moveBtnEle).perform();
                    List<MoveEntity> list = getMoveEntity(distance);
                    for (MoveEntity moveEntity : list) {
                        actions.moveByOffset(moveEntity.getX(), moveEntity.getY()).perform();
                        Thread.sleep(moveEntity.getSleepTime());
                    }
                    actions.release(moveBtnEle).perform();
                    Thread.sleep(200);
                    String gtType = driver.findElement(By.cssSelector(".gt_info_type")).getText();
                    String gtInfo = driver.findElement(By.cssSelector(".gt_info_content")).getText();
                    /**
                     * gtType, gtInfo：
                     *      再来一次:哇哦～怪物吃了拼图 3 秒后重试
                     *      验证失败:拖动滑块将悬浮图像正确拼合
                     *      出现错误:请关闭验证重试
                     */
                    if (gtType.equals("再来一次:") || gtType.equals("验证失败:") || gtType.equals("出现错误:")) {
                        Thread.sleep(1000);
                        recognize = false;
                    } else {
                        System.out.println("Login Success!");
                        recognize = true;
                    }
                } while (!recognize);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }


    public static void waitBy(By by) {
        // wait.until(ExpectedConditions.presenceOfElementLocated(by));
        wait.until((ExpectedCondition<Boolean>) d -> {
            WebElement element = driver.findElement(by);
            if (element != null) {
                return true;
            }
            return false;
        });
    }

    /**
     * 计算需要平移的距离
     *
     * @return
     * @throws IOException
     */
    private static int calMoveDistance() throws IOException {
        String fullImageUrlXpath = "//div[@class='gt_cut_fullbg_slice']";
        String cutImageUrlXpath = "//div[@class='gt_cut_bg_slice']";
        String fullImageUrl = getImageUrlByXpath(fullImageUrlXpath);
        String cutImageUrl = getImageUrlByXpath(cutImageUrlXpath);

        // 1.下载原始乱序图
        FileUtils.copyURLToFile(new URL(fullImageUrl), new File(BASE_PATH + FULL_IMAGE_NAME + "-origin.jpg"));
        FileUtils.copyURLToFile(new URL(cutImageUrl), new File(BASE_PATH + CUT_IMAGE_NAME + "-origin.jpg"));

        // 2.获取小图坐标
        getLocation();

        // 3.还原成正常图
        restoreImage(FULL_IMAGE_NAME);
        restoreImage(CUT_IMAGE_NAME);

        BufferedImage fullBI = ImageIO.read(new File(BASE_PATH + FULL_IMAGE_NAME + ".jpg"));
        BufferedImage cutBI = ImageIO.read(new File(BASE_PATH + CUT_IMAGE_NAME + ".jpg"));

        // 小方块距离左边界距离
        int distance = 6;
        // 对比色素点的起始位置，避免小方块本身的色素影响
        int startWidth = 60;
        for (int i = startWidth; i < cutBI.getWidth(); i++) {
            for (int j = 0; j < cutBI.getHeight(); j++) {
                int[] fullRgb = new int[3];
                fullRgb[0] = (fullBI.getRGB(i, j) & 0xff0000) >> 16;
                fullRgb[1] = (fullBI.getRGB(i, j) & 0xff00) >> 8;
                fullRgb[2] = (fullBI.getRGB(i, j) & 0xff);

                int[] cutRgb = new int[3];
                cutRgb[0] = (cutBI.getRGB(i, j) & 0xff0000) >> 16;
                cutRgb[1] = (cutBI.getRGB(i, j) & 0xff00) >> 8;
                cutRgb[2] = (cutBI.getRGB(i, j) & 0xff);
                if (difference(fullRgb, cutRgb) > 255) {
                    int moveDistance = i - distance;
                    System.out.println("move distance: " + moveDistance);
                    return moveDistance;
                }
            }
        }
        throw new RuntimeException("No position to be shifted was found");
    }

    private static int difference(int[] a, int[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
    }

    /**
     * 获取各个图片坐标数组
     */
    private static void getLocation() {
        // 完整图跟缺口图，每个小图片的坐标是一致的，所以找完整图（或者缺口图）的所有小图坐标即可
        List<WebElement> elements = driver.findElements(By.xpath("//div[@class='gt_cut_fullbg_slice']"));
        int i = 0;
        Pattern pattern = Pattern.compile(".*background-position: (.*?)px (.*?)px.*");
        for (WebElement ele : elements) {
            Matcher matcher = pattern.matcher(ele.getAttribute("style"));
            if (matcher.find()) {
                String width = matcher.group(1);
                String height = matcher.group(2);
                locations[i][0] = Integer.parseInt(width);
                locations[i++][1] = Integer.parseInt(height);
            } else {
                throw new RuntimeException("getLocation matcher fail!");
            }
        }
    }

    /**
     * 还原图片
     *
     * @param fileName 还原图片的名字
     */
    private static void restoreImage(String fileName) {
        /**
        * 每张小图的位置已经确定在locations里，即
         * location[0][0],location[0][1]即为第一张小图在乱序图中的起始位置，命名文件1，同理
         * location[1][0],location[1][1]为第二张小图在乱序图中的起始位置，命名文件2，以此类推，把整张乱序图切成2*26份
         * 即每个小图按照文件顺序拼接起来为还原图
        */
        String tmpPath = BASE_PATH + "tmp/";
        for (int i = 0; i < 52; i++) {
            clipPic(BASE_PATH + fileName + "-origin.jpg", tmpPath + fileName + i + ".jpg", -locations[i][0], -locations[i][1], 10, 58);
        }

        // 上半部分图片路径
        String[] ups = new String[26];
        String[] downs = new String[26];
        for (int i = 0; i < 26; i++) {
            ups[i] = String.format(tmpPath + fileName + "%d.jpg", i);
            downs[i] = String.format(tmpPath + fileName + "%d.jpg", i + 26);
        }

        // 合并上半部分、下半部分
        mergeImage(ups, 1, tmpPath + fileName + "-up.jpg");
        mergeImage(downs, 1, tmpPath + fileName + "-down.jpg");

        // 合并上下半部分的两个大图
        String[] ud = new String[]{tmpPath + fileName + "-up.jpg", tmpPath + fileName + "-down.jpg"};
        mergeImage(ud, 2, BASE_PATH + fileName + ".jpg");

        // 删除产生的中间图片
        deleteFiles(new File(tmpPath));
    }

    /**
     * 获取图片路径
     *
     * @param xpath
     * @return
     */
    private static String getImageUrlByXpath(String xpath) {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
        String style = driver.findElementsByXPath(xpath).get(0).getAttribute("style");
        String url = null;
        Pattern pattern = Pattern.compile("url\\(\"(.*)\"\\)");
        Matcher matcher = pattern.matcher(style);
        if (matcher.find()) {
            url = matcher.group(1).replace(".webp", ".jpg");
        }
        return url;
    }

    /**
     * 裁剪图片
     *
     * @param srcFile 图片路径
     * @param outFile 图片切完后的路径
     * @param x       坐标x
     * @param y       坐标y
     * @param width   宽度
     * @param height  长度
     * @return
     */
    private static boolean clipPic(String srcFile, String outFile, int x, int y, int width, int height) {
        FileInputStream is = null;
        ImageInputStream iis = null;
        try {
            if (!new File(srcFile).exists()) {
                return false;
            }
            is = new FileInputStream(srcFile);
            String ext = srcFile.substring(srcFile.lastIndexOf(".") + 1);
            Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName(ext);
            ImageReader reader = it.next();
            iis = ImageIO.createImageInputStream(is);
            reader.setInput(iis, true);
            ImageReadParam param = reader.getDefaultReadParam();
            Rectangle rect = new Rectangle(x, y, width, height);
            param.setSourceRegion(rect);
            BufferedImage bi = reader.read(0, param);
            File tempOutFile = new File(outFile);
            if (!tempOutFile.exists()) {
                tempOutFile.mkdirs();
            }
            ImageIO.write(bi, ext, new File(outFile));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
                if (iis != null) {
                    iis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    /**
     * 图片拼接 （注意：必须两张图片长宽一致）
     *
     * @param files      要拼接的文件列表
     * @param type       1横向拼接，2 纵向拼接
     * @param targetFile 输出文件
     */
    private static void mergeImage(String[] files, int type, String targetFile) {
        int length = files.length;
        File[] src = new File[length];
        BufferedImage[] images = new BufferedImage[length];
        int[][] ImageArrays = new int[length][];
        for (int i = 0; i < length; i++) {
            try {
                src[i] = new File(files[i]);
                images[i] = ImageIO.read(src[i]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            int width = images[i].getWidth();
            int height = images[i].getHeight();
            ImageArrays[i] = new int[width * height];
//            ImageArrays[i] = images[i].getRGB(0, 0, width, height, ImageArrays[i], 0, width);
            images[i].getRGB(0, 0, width, height, ImageArrays[i], 0, width);
        }
        int newHeight = 0;
        int newWidth = 0;
        for (int i = 0; i < images.length; i++) {
            // 横向
            if (type == 1) {
                newHeight = newHeight > images[i].getHeight() ? newHeight : images[i].getHeight();
                newWidth += images[i].getWidth();
            } else if (type == 2) {// 纵向
                newWidth = newWidth > images[i].getWidth() ? newWidth : images[i].getWidth();
                newHeight += images[i].getHeight();
            }
        }
        if (type == 1 && newWidth < 1) {
            return;
        }
        if (type == 2 && newHeight < 1) {
            return;
        }
        // 生成新图片
        try {
            BufferedImage ImageNew = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            int height_i = 0;
            int width_i = 0;
            for (int i = 0; i < images.length; i++) {
                if (type == 1) {
                    ImageNew.setRGB(width_i, 0, images[i].getWidth(), newHeight, ImageArrays[i], 0,
                            images[i].getWidth());
                    width_i += images[i].getWidth();
                } else if (type == 2) {
                    ImageNew.setRGB(0, height_i, newWidth, images[i].getHeight(), ImageArrays[i], 0, newWidth);
                    height_i += images[i].getHeight();
                }
            }
            //输出想要的图片
            ImageIO.write(ImageNew, targetFile.split("\\.")[1], new File(targetFile));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除目录及其子文件，系统退出后删除
     *
     * @param dir
     */
    private static void deleteFiles(File dir) {
        if (dir.exists()) {
            if (dir.isDirectory()) {
                for (File zFile : Objects.requireNonNull(dir.listFiles())) {
                    deleteFiles(zFile);
                }
            }
            dir.delete();
        }
    }

    /**
     * 分割移动距离，变成多个对象，模拟人工拖拽轨迹
     *
     * @param distance
     * @return
     */
    private static List<MoveEntity> getMoveEntity(int distance) {
        List<MoveEntity> list = new ArrayList<>();
        int i = 0;
        do {
            MoveEntity moveEntity = new MoveEntity();
            int r = RandomUtils.nextInt(5, 8);
            moveEntity.setX(r);
            moveEntity.setY(RandomUtils.nextInt(0, 1) == 1 ? RandomUtils.nextInt(0, 2) : 0 - RandomUtils.nextInt(0, 2));
            int s = 0;
            if (i / Double.valueOf(distance) > 0.05) {
                if (i / Double.valueOf(distance) < 0.85) {
                    s = RandomUtils.nextInt(2, 5);
                } else {
                    s = RandomUtils.nextInt(10, 15);
                }
            } else {
                s = RandomUtils.nextInt(20, 30);
            }
            moveEntity.setSleepTime(s);
            list.add(moveEntity);
            i = i + r;
        } while (i <= distance + 5);
        boolean cc = i > distance;
        for (int j = 0; j < Math.abs(distance - i); ) {
            int r = RandomUtils.nextInt(1, 3);
            MoveEntity moveEntity = new MoveEntity();
            moveEntity.setX(cc ? -r : r);
            moveEntity.setY(0);
            moveEntity.setSleepTime(RandomUtils.nextInt(100, 200));
            list.add(moveEntity);
            j = j + r;
        }
        return list;
    }

    static class MoveEntity {
        private int x;
        private int y;
        private int sleepTime;//毫秒

        public MoveEntity() {

        }

        public MoveEntity(int x, int y, int sleepTime) {
            this.x = x;
            this.y = y;
            this.sleepTime = sleepTime;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getSleepTime() {
            return sleepTime;
        }

        public void setSleepTime(int sleepTime) {
            this.sleepTime = sleepTime;
        }
    }
}