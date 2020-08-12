import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
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
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HuXiu {
    // 所有图片的前缀路径
    private static String basePath = "src/main/resources/";
    // 完整的图
    private static String FULL_IMAGE_NAME = "full-image";
    // 有缺口的图
    private static String BG_IMAGE_NAME = "cut-image";
    // 存储 2 * 26 = 52 个图的位置信息，-157px -58px;
    private static int[][] moveArray = new int[52][2];
    private static boolean moveArrayInit = false;
    private static String INDEX_URL = "https://www.huxiu.com/";
    private static WebDriver driver;

    static {
        System.getProperties().setProperty("webdriver.Chrome.driver", "\\chromedriver.exe");
        driver = new ChromeDriver();
        driver.manage().window().setSize(new org.openqa.selenium.Dimension(1600, 768));
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            try {
                invoke();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        driver.quit();
    }

    private static void invoke() throws IOException, InterruptedException {
        //设置input参数
        driver.get(INDEX_URL);

        WebDriverWait wait = new WebDriverWait(driver, 5);
        // 等待登录按钮出现
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@class='js-login']")));
        // 单击【登录】按钮，弹出滑动验证码框框
        driver.findElement(By.xpath("//*[@class='js-login']")).click();
        // 拖动按钮By
        By moveBtnBy = By.xpath("//*[@class='gt_slider_knob gt_show']");
        // 等待拖动按钮出现，即拖动框出现
        wait.until(ExpectedConditions.presenceOfElementLocated(moveBtnBy));
        int i = 0;
        while (i++ < 15) {
            int distance = getMoveDistance(driver);
            move(driver, driver.findElement(moveBtnBy), distance - 6);
            By gtTypeBy = By.cssSelector(".gt_info_type");
            By gtInfoBy = By.cssSelector(".gt_info_content");
            wait.until(ExpectedConditions.presenceOfElementLocated(gtTypeBy));
            wait.until(ExpectedConditions.presenceOfElementLocated(gtTypeBy));
            String gtType = driver.findElement(gtTypeBy).getText();
            String gtInfo = driver.findElement(gtInfoBy).getText();
            System.out.println(gtType + "---" + gtInfo);
            if (!gtType.equals("再来一次:") && !gtType.equals("验证失败:")) {
                Thread.sleep(4000);
                break;
            }
            Thread.sleep(4000);
        }
    }

    /**
     * 移动
     * @param driver
     * @param element
     * @param distance
     * @throws InterruptedException
     */
    public static void move(WebDriver driver, WebElement element, int distance) throws InterruptedException {
        System.out.println("应平移距离：" + distance);
        int moveX = new Random().nextInt(8) - 5;
        int moveY = 1;
        Actions actions = new Actions(driver);
        actions.clickAndHold(element).perform();
        List<MoveEntity> list = getMoveEntity(distance);
        for(MoveEntity moveEntity : list){
            actions.moveByOffset(moveEntity.getX(), moveEntity.getY()).perform();
            Thread.sleep(moveEntity.getSleepTime());
        }
        Thread.sleep(200);
        actions.release(element).perform();
    }

    /**
     * 计算需要平移的距离
     * @param driver
     * @return
     * @throws IOException
     */
    public static int getMoveDistance(WebDriver driver) throws IOException {
        String pageSource = driver.getPageSource();
        String fullImageUrl = getFullImageUrl(pageSource);
        FileUtils.copyURLToFile(new URL(fullImageUrl), new File(basePath + FULL_IMAGE_NAME + ".jpg"));
        String cutImageUrl = getCutImageUrl(pageSource);
        FileUtils.copyURLToFile(new URL(cutImageUrl), new File(basePath + BG_IMAGE_NAME + ".jpg"));
        initMoveArray(driver);
        restoreImage(FULL_IMAGE_NAME);
        restoreImage(BG_IMAGE_NAME);
        BufferedImage fullBI = ImageIO.read(new File(basePath + "result/" + FULL_IMAGE_NAME + "result3.jpg"));
        BufferedImage bgBI = ImageIO.read(new File(basePath + "result/" + BG_IMAGE_NAME + "result3.jpg"));
        for (int i = 0; i < bgBI.getWidth(); i++) {
            for (int j = 0; j < bgBI.getHeight(); j++) {
                int[] fullRgb = new int[3];
                fullRgb[0] = (fullBI.getRGB(i, j) & 0xff0000) >> 16;
                fullRgb[1] = (fullBI.getRGB(i, j) & 0xff00) >> 8;
                fullRgb[2] = (fullBI.getRGB(i, j) & 0xff);
                int[] bgRgb = new int[3];
                bgRgb[0] = (bgBI.getRGB(i, j) & 0xff0000) >> 16;
                bgRgb[1] = (bgBI.getRGB(i, j) & 0xff00) >> 8;
                bgRgb[2] = (bgBI.getRGB(i, j) & 0xff);
                if (difference(fullRgb, bgRgb) > 255) {
                    return i;
                }
            }
        }
        throw new RuntimeException("未找到需要平移的位置");
    }

    private static int difference(int[] a, int[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
    }

    /**
     * 获取move数组
     * @param driver
     */
    private static void initMoveArray(WebDriver driver) {
        if (moveArrayInit) {
            return;
        }
        Document document = Jsoup.parse(driver.getPageSource());
        Elements elements = document.select("[class=gt_cut_bg gt_show]").first().children();
        int i = 0;
        for (Element element : elements) {
            Pattern pattern = Pattern.compile(".*background-position: (.*?)px (.*?)px.*");
            Matcher matcher = pattern.matcher(element.toString());
            if (matcher.find()) {
                String width = matcher.group(1);
                String height = matcher.group(2);
                moveArray[i][0] = Integer.parseInt(width);
                moveArray[i++][1] = Integer.parseInt(height);
            } else {
                throw new RuntimeException("解析异常");
            }
        }
        moveArrayInit = true;
    }

    /**
     * 还原图片
     *
     * @param type
     */
    private static void restoreImage(String type) {
        //把图片裁剪为2 * 26份
        for (int i = 0; i < 52; i++) {
            clipPic(basePath + type + ".jpg"
                    , basePath + "result/" + type + i + ".jpg", -moveArray[i][0], -moveArray[i][1], 10, 58);
        }
        //拼接图片
        String[] b = new String[26];
        for (int i = 0; i < 26; i++) {
            b[i] = String.format(basePath + "result/" + type + "%d.jpg", i);
        }
        mergeImage(b, 1, basePath + "result/" + type + "result1.jpg");
        //拼接图片
        String[] c = new String[26];
        for (int i = 0; i < 26; i++) {
            c[i] = String.format(basePath + "result/" + type + "%d.jpg", i + 26);
        }
        mergeImage(c, 1, basePath + "result/" + type + "result2.jpg");
        mergeImage(new String[]{basePath + "result/" + type + "result1.jpg",
                basePath + "result/" + type + "result2.jpg"}, 2, basePath + "result/" + type + "result3.jpg");
        //删除产生的中间图片
        for (int i = 0; i < 52; i++) {
            new File(basePath + "result/" + type + i + ".jpg").deleteOnExit();
        }
        new File(basePath + "result/" + type + "result1.jpg").deleteOnExit();
        new File(basePath + "result/" + type + "result2.jpg").deleteOnExit();
    }

    /**
     * 获取原始图url
     * @param pageSource
     * @return
     */
    private static String getFullImageUrl(String pageSource) {
        String url = null;
        Document document = Jsoup.parse(pageSource);
        String style = document.select("[class=gt_cut_fullbg_slice]").first().attr("style");
        Pattern pattern = Pattern.compile("url\\(\"(.*)\"\\)");
        Matcher matcher = pattern.matcher(style);
        if (matcher.find()) {
            url = matcher.group(1);
        }
        url = url.replace(".webp", ".jpg");
        System.out.println(url);
        return url;
    }

    /**
     * 获取带缺口的url
     * @param pageSource
     * @return
     */
    private static String getCutImageUrl(String pageSource) {
        String url = null;
        Document document = Jsoup.parse(pageSource);
        String style = document.select(".gt_cut_bg_slice").first().attr("style");
        Pattern pattern = Pattern.compile("url\\(\"(.*)\"\\)");
        Matcher matcher = pattern.matcher(style);
        if (matcher.find()) {
            url = matcher.group(1);
        }
        url = url.replace(".webp", ".jpg");
        System.out.println(url);
        return url;
    }

    /**
     * 裁剪图片
     * @param srcFile
     * @param outFile
     * @param x
     * @param y
     * @param width
     * @param height
     * @return
     */
    public static boolean clipPic(String srcFile, String outFile, int x, int y, int width, int height) {
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
     * 图片拼接 （注意：必须两张图片长宽一致哦）
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
            ImageArrays[i] = images[i].getRGB(0, 0, width, height, ImageArrays[i], 0, width);
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
     * 分割移动距离，变成多个对象，模拟人工拖拽轨迹
     * @param distance
     * @return
     */
    public static List<MoveEntity> getMoveEntity(int distance){
        List<MoveEntity> list = new ArrayList<>();
        int i = 0;
        do {
            MoveEntity moveEntity = new MoveEntity();
            int r = RandomUtils.nextInt(5, 8);
            moveEntity.setX(r);
            moveEntity.setY(RandomUtils.nextInt(0, 1)==1?RandomUtils.nextInt(0, 2):0-RandomUtils.nextInt(0, 2));
            int s = 0;
            if(i/Double.valueOf(distance)>0.05){
                if(i/Double.valueOf(distance)<0.85){
                    s = RandomUtils.nextInt(2, 5);
                }else {
                    s = RandomUtils.nextInt(10, 15);
                }
            }else{
                s = RandomUtils.nextInt(20, 30);
            }
            moveEntity.setSleepTime(s);
            list.add(moveEntity);
            i = i + r;
        } while (i <= distance+5);
        boolean cc= i>distance;
        for (int j = 0; j < Math.abs(distance-i); ) {
            int r = RandomUtils.nextInt(1, 3);
            MoveEntity moveEntity = new MoveEntity();
            moveEntity.setX(cc?-r:r);
            moveEntity.setY(0);
            moveEntity.setSleepTime(RandomUtils.nextInt(100, 200));
            list.add(moveEntity);
            j = j+r;
        }
        return list;
    }

    static class MoveEntity{
        private int x;
        private int y;
        private int sleepTime;//毫秒

        public MoveEntity(){

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