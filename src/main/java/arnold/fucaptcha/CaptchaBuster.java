package arnold.fucaptcha;

import lombok.Getter;
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
    Actions actions;
    Ollama ollama;
    boolean headless = false;
    String modelName = "mistral-small3.2:24b";
    By iframeLocator = By.cssSelector("iframe[title][role]");
    By captchaStartButtonLocator = By.className("recaptcha-checkbox-border");
    By captchaChoiceButtonLocator = By.cssSelector("[role='button'].rc-imageselect-tile");
    By iframeSelectionFrameLocator = By.cssSelector("[title='recaptcha challenge expires in two minutes']");

    @Getter
    enum CaptchaSelectionAttributes{
        CheckedChoiceClass("rc-imageselect-tileselected");

        final String value;

        CaptchaSelectionAttributes(String value){
            this.value = value;
        }
    }

    public CaptchaBuster(RemoteWebDriver driver, Ollama ollama){
        this.driver = driver;
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

            WebElement captchaStartButton = driver.findElement(captchaStartButtonLocator);
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
                    List<WebElement> captchaChoiceButtons = driver.findElements(captchaChoiceButtonLocator);
                    correctOptions = new ArrayList<>();
                    File screenshot = driver.getScreenshotAs(OutputType.FILE);
                    BufferedImage fullImg = ImageIO.read(screenshot);
                    for (WebElement element : captchaChoiceButtons){
                        if (forbiddenIndexes.contains(captchaChoiceButtons.indexOf(element)) ||
                                Objects.requireNonNull(element.getAttribute("class"))
                                        .contains(CaptchaSelectionAttributes.CheckedChoiceClass.getValue())
                        )
                            continue;

                        BufferedImage eleScreenshot;
                        int scale = headless ? 1 : 2;

                        Point point = element.getLocation();
                        int eleWidth = (element.getSize().getWidth()) * scale;
                        int eleHeight = (element.getSize().getHeight()) * scale;
                        eleScreenshot = fullImg.getSubimage((point.getX() + 80) * scale, (point.getY() + 10) * scale, eleWidth, eleHeight);

                        File croppedSS = new File("element-crop#" + captchaChoiceButtons.indexOf(element) + ".png");
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
                        else forbiddenIndexes.add(captchaChoiceButtons.indexOf(element));
                    }

                    System.out.println("Number of correct choices: " + correctOptions.size());

                    for(WebElement element : correctOptions)
                        if (!Objects.requireNonNull(element.getAttribute("class"))
                                .contains(CaptchaSelectionAttributes.CheckedChoiceClass.getValue())
                        ){
                            if (correctOptions.indexOf(element) > 0){
                                WebElement zipElementI = correctOptions.get(correctOptions.indexOf(element) - 1);

                                smoothCursorMovement(zipElementI.getLocation().x, zipElementI.getLocation().y , element.getLocation().x + 80, element.getLocation().y, 50, 15);
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
                    }
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

    private void clickTowardsElement(WebElement element){
        actions.moveToElement(element)
                .click()
                .build()
                .perform();
    }

    public static void smoothCursorMovement(int startX, int startY, int endX, int endY, int steps, int delayMs) throws AWTException {
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