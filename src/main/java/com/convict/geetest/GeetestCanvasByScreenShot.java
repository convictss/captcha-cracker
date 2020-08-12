package com.convict.geetest;

import org.apache.commons.lang3.RandomUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author: Convict.Yellow
 * @date: 2020/8/12 11:12
 * @description: selenium识别极验滑动验证码(canvas)，下载图片方式为全屏截图，截出缺口图、完整图
 *
 * 1.下载缺口图、完整图、全页面图
 * 2.比较缺口图、完整图，获得移动距离
 * 3.根据移动距离，模拟真人鼠标移动
 */
public class GeetestCanvasByScreenShot {

    // 所有图片存储路径
    private static final String BASE_PATH = "D:/";
    // 完整图
    private static final String FULL_IMAGE_NAME = "full.png";
    // 缺口图
    private static final String CUT_IMAGE_NAME = "cut.png";

    private static ChromeDriver driver;

    static {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless");
        driver = new ChromeDriver(options);
    }

    public static void main(String[] args) {
        int showCount = 10;
        try {
            for (int i = 0; i < showCount; i++) {
                driver.manage().window().setSize(new Dimension(1024, 768));
                driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
                driver.manage().timeouts().pageLoadTimeout(10, TimeUnit.SECONDS);
                driver.get("https://www.geetest.com/demo/slide-bind.html");
                Thread.sleep(2 * 1000);
                driver.findElement(By.className("btn")).click();
                Thread.sleep(3 * 1000);
                Actions actions = new Actions(driver);

                // 全局页面
                byte[] pageBytes = driver.getScreenshotAs(OutputType.BYTES);
                BufferedImage pageBI = ImageIO.read(new ByteArrayInputStream(pageBytes));
                ImageIO.write(pageBI, "png", new File(BASE_PATH + "page.png"));

                // 设置完整图可见
                driver.executeScript("document.getElementsByClassName('geetest_canvas_fullbg')[0].setAttribute('style', 'display: block')");
                // 下载完整图
                WebElement fullEle = driver.findElement(By.cssSelector("canvas[class='geetest_canvas_fullbg geetest_fade geetest_absolute']"));
                BufferedImage fullBI = pageBI.getSubimage(fullEle.getLocation().getX(), fullEle.getLocation().getY(), fullEle.getSize().getWidth(), fullEle.getSize().getHeight());
                ImageIO.write(fullBI, "png", new File(BASE_PATH + FULL_IMAGE_NAME));
                // 隐藏完整图可见
                driver.executeScript("document.getElementsByClassName('geetest_canvas_fullbg')[0].setAttribute('style', 'display: none')");

                // 下载缺口图
                WebElement cutEle = driver.findElement(By.cssSelector("canvas[class='geetest_canvas_bg geetest_absolute']"));
                BufferedImage cutBI = pageBI.getSubimage(cutEle.getLocation().getX(), cutEle.getLocation().getY(), cutEle.getSize().getWidth(), cutEle.getSize().getHeight());
                ImageIO.write(cutBI, "png", new File(BASE_PATH + CUT_IMAGE_NAME));

                WebElement button = driver.findElement(By.className("geetest_slider_button"));
                actions.clickAndHold(button).perform();
                int moveDistance = calMoveDistance();

                // 移动
                List<MoveEntity> entities = getMoveEntity(moveDistance);
                for (MoveEntity moveEntity : entities) {
                    actions.moveByOffset(moveEntity.getX(), moveEntity.getY()).perform();
                    Thread.sleep(moveEntity.getSleepTime());
                }
                actions.release(button).perform();
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    public static List<MoveEntity> getMoveEntity(int distance) {
        List<MoveEntity> list = new ArrayList<>();
        int i = 0;
        do {
            MoveEntity moveEntity = new MoveEntity();
            int r = RandomUtils.nextInt(5, 8);
            moveEntity.setX(r);
            moveEntity.setY(RandomUtils.nextInt(0, 1) == 1 ? RandomUtils.nextInt(0, 2) : 0 - RandomUtils.nextInt(0, 2));
            int s = 0;
            if (i / (double) distance > 0.05) {
                if (i / (double) distance < 0.85) {
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

        MoveEntity() {

        }

        public MoveEntity(int x, int y, int sleepTime) {
            this.x = x;
            this.y = y;
            this.sleepTime = sleepTime;
        }

        int getX() {
            return x;
        }

        void setX(int x) {
            this.x = x;
        }

        int getY() {
            return y;
        }

        void setY(int y) {
            this.y = y;
        }

        int getSleepTime() {
            return sleepTime;
        }

        void setSleepTime(int sleepTime) {
            this.sleepTime = sleepTime;
        }
    }

    /**
     * 根据original.png和slider.png计算需要移动的距离
     *
     * @return
     */
    private static int calMoveDistance() {
        // 小方块距离左边界距离
        int distance = 6;
        // 对比色素点的起始位置，避免小方块本身的色素影响
        int startWidth = 60;
        try {
            BufferedImage fullBI = ImageIO.read(new File(BASE_PATH + FULL_IMAGE_NAME));
            BufferedImage cutBI = ImageIO.read(new File(BASE_PATH + CUT_IMAGE_NAME));
            for (int i = startWidth; i < fullBI.getWidth(); i++) {
                for (int j = 0; j < fullBI.getHeight(); j++) {
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
        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new RuntimeException("No position to be shifted was found!");
    }

    private static int difference(int[] a, int[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
    }
}
