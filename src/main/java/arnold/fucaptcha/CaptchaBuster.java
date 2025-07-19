package arnold.fucaptcha;

import arnold.fucaptcha.omniparser.OmniParser;
import arnold.fucaptcha.omniparser.models.ElementClassificationResponse;
import arnold.fucaptcha.vllm.CaptchaTargetIndexes;
import collections.Pair;
import ollama.Ollama;
import ollama.models.inference.InferenceModel;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import utils.FileUtilities;
import utils.NumericUtilities;
import java.io.File;
import java.io.IOException;
import java.util.List;
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
                centerElement(selectionIframe, driver);

                String ssPath = getSS();
//                File croppedAndAnnotatedSS = new File(ssPath);
                driver.switchTo().frame(selectionIframe);
                WebElement captchaSelectionFrame = driver.findElement(By.id("rc-imageselect"));


                //BufferedImage fullImg = ImageIO.read(croppedAndAnnotatedSS);
//
                //Point point = captchaSelectionFrame.getLocation();
//
                //int eleWidth = captchaSelectionFrame.getSize().getWidth();
                //int eleHeight = captchaSelectionFrame.getSize().getHeight();
//
                //BufferedImage eleScreenshot = fullImg.getSubimage(point.getX() + 80, point.getY(), eleWidth, eleHeight);
                //ImageIO.write(eleScreenshot, "png", croppedAndAnnotatedSS);
//
                //ImageIO.write(eleScreenshot, "png", croppedAndAnnotatedSS); // Or a new file if you don't want to overwrite
                ElementClassificationResponse classificationResponse = omni.upload(ssPath).idEnrichment();
                FileUtilities.saveDecodedImage(
                        classificationResponse.getSom_image_base64(),
                        new File("AnnotatedSS#" + new Random().nextInt() + ".png")
                );

                String captchaObject = getCaptchaObjectName(
                        modelName,
                        classificationResponse.getSom_image_base64(),
                        ollama
                ).getObjectName();

                System.out.println("Object: " + captchaObject);

                List<Integer> targetIndexes = getCaptchaTargetIndexes(
                        modelName,
                        classificationResponse.getSom_image_base64(),
                        captchaObject,
                        ollama
                ).getTargetIndexes();

                System.out.println("Indexes: " + targetIndexes);

                for(int index : targetIndexes){
                    ElementClassificationResponse.Element responseElement = classificationResponse.getParsed_content_list().get(index);
                    Pair<Double, Double> targetElementBBoxCenterPoint = getCenterPoint(responseElement.getBbox(), window);
                    WebElement captchaTarget = getClosestElementAt(
                            targetElementBBoxCenterPoint.alpha() - 80,
                            targetElementBBoxCenterPoint.beta(),
                            driver
                    );
                    clickTowardsElement(captchaTarget);
                }

                WebElement captchaVerifyButton = driver.findElement(By.id("recaptcha-verify-button"));

                getSS();
                captchaVerifyButton.click();

                System.out.println(targetIndexes);
                System.out.println("Checkpoint!");

            }
            while (!driver.findElements(By.id("rc-imageselect")).isEmpty());
        }
        catch (NoSuchElementException ignored){}
        finally {
            driver.switchTo().parentFrame();
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
        String captchaBustingPrompt = "what is the object of this captcha?";

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
}