package arnold;

import arnold.fucaptcha.CaptchaBuster;
import context.ContextStore;
import okhttp3.Headers;
import ollama.Ollama;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import utils.NumericUtilities;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AppTest {
    RemoteWebDriver chrome;
    FluentWait<RemoteWebDriver> wait;
    Ollama ollama;

    public static boolean headless = false;

    ChromeOptions options;

    @Before
    public void before(){
        ContextStore.loadProperties("secret.properties", "app.properties");
        String OWUIToken = ContextStore.get("OWUI-token");
        String ollamaBaseUrl = ContextStore.get("ollama-url");
        String UIParserBaseUrl = ContextStore.get("omni-parser-url");

        options = new ChromeOptions();
        if (headless) options.addArguments("--headless=new");

        chrome = new ChromeDriver(options);
        wait = new FluentWait<>(chrome)
                .withTimeout(Duration.ofSeconds(10))
                .pollingEvery(Duration.ofMillis(500))
                .withMessage("Waiting for element visibility...")
                .ignoring(WebDriverException.class);

        ollama = new Ollama(ollamaBaseUrl, Headers.of("Authorization", "Bearer " + OWUIToken));
    }

    @After
    public void after(){
        chrome.quit();
    }

    @Test
    public void crawl() throws IOException {
        String url = getQueryUrl("What is the difference between verification and validation");

        chrome.get("https://www.google.com/");
        wait.until(ExpectedConditions.visibilityOf(chrome.findElement(By.cssSelector("*"))));
        wait.until(ExpectedConditions.visibilityOf(chrome.findElement(By.partialLinkText("More options"))));

        try {
            By locator = By.xpath("//div[@role='none' and contains(text(), 'Reject all')]");
            WebElement rejectButton = chrome.findElement(locator);
            rejectButton.click();
        }
        catch (NoSuchElementException ignored){}

        WebElement searchBar = chrome.findElement(By.cssSelector("[role=\"search\"] [role=\"combobox\"]"));
        searchBar.sendKeys("What is the difference between verification and validation");
        searchBar.sendKeys(Keys.ENTER);

        CaptchaBuster buster = new CaptchaBuster(chrome, ollama);
        buster.bustIt();

        List<WebElement> results = chrome.findElements(By.cssSelector("#center_col div[data-rpos] span[jsaction] a[href]"));
        List<String> links = new ArrayList<>();
        for (WebElement element : results) {
            links.add(element.getAttribute("href"));
        }
        System.out.println(links);
    }

    public static String getQueryUrl(String query) {
        String baseQueryUrl = "https://www.google.com/search?q=";
        return baseQueryUrl + query.toLowerCase().replaceAll(" ", "+");
    }

    public static String getSS(RemoteWebDriver driver){
        try {Thread.sleep(3000);}
        catch (InterruptedException e) {throw new RuntimeException(e);}
        File SS = captureScreen("SS", "png", driver);
        return SS.getAbsolutePath();
    }

    public static File captureScreen(String name, String extension, RemoteWebDriver driver) {
        try {
            if (!extension.contains(".")) extension = "." + extension;
            name += "#"+ NumericUtilities.randomNumber(1,10000) + extension;
            File sourceFile = new File("screenshots");
            File fileDestination  = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(fileDestination, new File(sourceFile, name));

            System.out.println("Screenshot saved as; "+name+" at the \"screenshots\" file.");
            return fileDestination;
        }
        catch (Exception exception){
            exception.printStackTrace();
            return null;
        }
    }
}
