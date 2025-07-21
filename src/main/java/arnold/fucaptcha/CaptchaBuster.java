package arnold.fucaptcha;

import arnold.App;
import arnold.fucaptcha.omniparser.OmniParser;
import arnold.fucaptcha.omniparser.models.ElementClassificationResponse;
import arnold.fucaptcha.vllm.CaptchaTargetIndexes;
import collections.Pair;
import ollama.Ollama;
import ollama.models.inference.InferenceModel;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import utils.FileUtilities;
import utils.NumericUtilities;
import utils.arrays.ArrayUtilities;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class CaptchaBuster {
    RemoteWebDriver driver;
    OmniParser omni;
    Actions actions;
    Ollama ollama;
    String modelName = "mistral-small3.2:24b";
    By iframeLocator = By.cssSelector("iframe[title][role]");
    By iframeSelectionFrameLocator = By.cssSelector("[title='recaptcha challenge expires in two minutes']");

    public CaptchaBuster(RemoteWebDriver driver, OmniParser omniParser, Ollama ollama){
        this.driver = driver;
        this.omni = omniParser;
        this.ollama = ollama;

        actions = new Actions(this.driver);
    }

    public void bustIt() throws IOException {
        Dimension originalWindow = driver.manage().window().getSize();
        Dimension window = new Dimension(896, 896);
        driver.manage().window().setSize(window);

        try {
            WebElement iframe = driver.findElement(iframeLocator);
            driver.switchTo().frame(iframe);

            WebElement captchaStartButton = driver.findElement(By.className("recaptcha-checkbox-border"));
            clickTowardsElement(captchaStartButton);

            do {
                driver.switchTo().parentFrame();
                WebElement selectionIframe = driver.findElement(iframeSelectionFrameLocator);
//TODO write a script to download the image from captcha iframe, upscale it, divide it programatically, have each image analysed, index images, click the ones with the object
                String ssPath = getSS();
                driver.switchTo().frame(selectionIframe);

                String captchaObject = getCaptchaObjectName(
                        modelName,
                        FileUtilities.getEncodedString(ssPath),
                        ollama
                ).getObjectName();

                System.out.println("Object: " + captchaObject);

                int correctChoiceCounter = 0;
                List<WebElement> correctOptions;
                List<Integer> forbiddenIndexes  = new ArrayList<>();
                do {
                    List<WebElement> captchaOptions = driver.findElements(By.cssSelector("[role='button'].rc-imageselect-tile"));
                    correctOptions = new ArrayList<>();
                    File screenshot = driver.getScreenshotAs(OutputType.FILE);
                    BufferedImage fullImg = ImageIO.read(screenshot);
                    for (WebElement element : captchaOptions){
                        if (forbiddenIndexes.contains(captchaOptions.indexOf(element)) || Objects.requireNonNull(element.getAttribute("class")).contains("rc-imageselect-tileselected"))
                            continue;

                        BufferedImage eleScreenshot;
                        int scale = App.headless ? 1 : 2;

                        Point point = element.getLocation();
                        int eleWidth = (element.getSize().getWidth()) * scale;
                        int eleHeight = (element.getSize().getHeight()) * scale;
                        eleScreenshot = fullImg.getSubimage((point.getX() + 80) * scale, (point.getY() + 10) * scale, eleWidth, eleHeight);

                        //boolean zipAround = true;
                        //do {
                        //    try {
                        //        WebElement zipElementI = ArrayUtilities.getRandomItemFrom(driver.findElements(By.cssSelector("*")));
                        //        actions.moveToElement(zipElementI)
                        //                .build()
                        //                .perform();
                        //        zipAround = false;
                        //    }
                        //    catch (ElementNotInteractableException ignored){}
                        //}
                        //while (zipAround);


                        File croppedSS = new File("element-crop#" + captchaOptions.indexOf(element) + ".png");
                        ImageIO.write(eleScreenshot, "png", croppedSS);

                        boolean containsObject = captchaBoxContainsObject(
                                modelName,
                                FileUtilities.getEncodedString(croppedSS),
                                captchaObject,
                                ollama
                        );

                        if (containsObject && !correctOptions.contains(element)) {
                            correctOptions.add(element);
                            correctChoiceCounter++;
                        }
                        else forbiddenIndexes.add(captchaOptions.indexOf(element));
                    }

                    System.out.println("Number of correct choices: " + correctOptions.size());

                    for(WebElement element : correctOptions)
                        if (!Objects.requireNonNull(element.getAttribute("class")).contains("rc-imageselect-tileselected")){
                            if (correctOptions.indexOf(element) > 0){
                                WebElement zipElementI = correctOptions.get(correctOptions.indexOf(element) - 1);

                                moveMouseSmoothly(zipElementI.getLocation().x, zipElementI.getLocation().y , element.getLocation().x + 80, element.getLocation().y, 50, 15);
                                actions.click(element).build().perform();
                            }
                            else
                                actions.pause(Duration.of(1, ChronoUnit.SECONDS))
                                    .moveByOffset(10,  15)
                                    .scrollToElement(element)
                                    .pause(Duration.of(1, ChronoUnit.SECONDS))
                                    .moveToElement(element)
                                    .pause(Duration.of(500, ChronoUnit.MILLIS))
                                    .click()
                                    .build()
                                    .perform();

                        }

                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }//.rc-imageselect-tileselected
                }
                while (!correctOptions.isEmpty() && correctChoiceCounter < 10);

                WebElement captchaVerifyButton = driver.findElement(By.id("recaptcha-verify-button"));
                getSS();
                actions.moveToElement(captchaVerifyButton)
                        .click(captchaVerifyButton)
                        .build()
                        .perform();

                System.out.println("Checkpoint!");

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            while (!driver.findElements(By.id("rc-imageselect")).isEmpty());
        }
        catch (NoSuchElementException ignored){} catch (AWTException e) {
            throw new RuntimeException(e);
        } finally {
            driver.switchTo().parentFrame();
            driver.manage().window().setSize(originalWindow);
        }
    }

    private String getSS(){
        try {Thread.sleep(3000);}
        catch (InterruptedException e) {throw new RuntimeException(e);}
        File SS = captureScreen("SS", "png");
        return SS.getAbsolutePath();
    }

    private File captureScreen(String name, String extension) {
        try {
            if (!extension.contains(".")) extension = "." + extension;
            name += "#"+ NumericUtilities.randomNumber(1,10000) + extension;
            File sourceFile = new File("screenshots");
            File fileDestination  = (driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(fileDestination, new File(sourceFile, name));

            System.out.println("Screenshot saved as; "+name+" at the \"screenshots\" file.");
            return fileDestination;
        }
        catch (Exception exception){
            exception.printStackTrace();
            return null;
        }
    }

    private CaptchaTargetObject getCaptchaObjectName(
            String modelName,
            String base64Image,
            Ollama ollama
    ){
        System.out.println("Assessing the Captcha object...");
        String captchaBustingPrompt = "select all images with a {objectName} \n" +
                "What is 'objectName'?";

        InferenceModel inferenceModel = new InferenceModel.Builder()
                .model(modelName)
                .prompt(captchaBustingPrompt)
                .temperature(1.0)
                .images(List.of(base64Image))
                .build();

        return ollama.inference(
                inferenceModel,
                CaptchaTargetObject.class,
                "objectName"
        );
    }

    private boolean captchaBoxContainsObject(
            String modelName,
            String base64Image,
            String objectName,
            Ollama ollama
    ){
        String captchaBustingPrompt = "Does the image partially or fully contain " + objectName + " with very high confidence?";

        InferenceModel inferenceModel = new InferenceModel.Builder()
                .model(modelName)
                .prompt(captchaBustingPrompt)
                .temperature(1.0)
                .images(List.of(base64Image))
                .build();

        return ollama.inference(
                inferenceModel,
                CaptchaVerdict.class,
                "boxContainsObject"
        ).getBoxContainsObject();
    }

    private CaptchaTargetIndexes getCaptchaTargetIndexes(
            String modelName,
            String base64Image,
            String objectName,
            Ollama ollama
    ){
        System.out.println("Assessing the Captcha...");
        String captchaBustingPrompt = "Which boxes contain the " + objectName + " object with high confidence";

        InferenceModel inferenceModel = new InferenceModel.Builder()
                .model(modelName)
                .prompt(captchaBustingPrompt)
                .temperature(1.0)
                .images(List.of(base64Image))
                .build();

        return ollama.inference(
                inferenceModel,
                CaptchaTargetIndexes.class,
                "targetIndexes"
        );
    }

    private static Pair<Double, Double> getCenterPoint(List<Double> bbox, Dimension window){
        double xRatio = (bbox.get(2) + bbox.get(0))/2;
        double yRatio = (bbox.get(3) + bbox.get(1))/2;
        return Pair.of(xRatio * window.width, yRatio * window.height * 0.745);
    }

    private static WebElement getClosestElementAt(double targetX, double targetY, RemoteWebDriver driver){
        return (WebElement) driver.executeScript(
                "var x = arguments[0], y = arguments[1];" +
                        "var elements = document.querySelectorAll('*');" +
                        "var closest = null, minDist = Infinity;" +
                        "elements.forEach(el => {" +
                        "  var rect = el.getBoundingClientRect();" +
                        "  var ex = rect.left + rect.width / 2;" +
                        "  var ey = rect.top + rect.height / 2;" +
                        "  var dist = Math.sqrt((ex - x) ** 2 + (ey - y) ** 2);" +
                        "  if (dist < minDist) { minDist = dist; closest = el; }" +
                        "});" +
                        "return closest;", targetX, targetY
        );
    }

    private void clickTowardsElement(WebElement element){
        actions.moveToElement(element)
                .click()
                .build()
                .perform();
    }

    private static WebElement centerElement(WebElement element, RemoteWebDriver driver) {
        String scrollScript = "var viewPortHeight = Math.max(document.documentElement.clientHeight, window.innerHeight || 0);"
                + "var elementTop = arguments[0].getBoundingClientRect().top;"
                + "window.scrollBy(0, elementTop-(viewPortHeight/2));";

        driver.executeScript(scrollScript, element);

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return element;
    }

    public static void moveMouseSmoothly(int startX, int startY, int endX, int endY, int steps, int delayMs) throws AWTException {
        Robot robot = new Robot();
        Random random = new Random();

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) (startX + (endX - startX) * t + random.nextGaussian()); // Add slight jitter
            int y = (int) (startY + (endY - startY) * t + random.nextGaussian());
            robot.mouseMove(x, y);
            robot.delay(delayMs + random.nextInt(10)); // Randomize delay slightly
        }
    }
}