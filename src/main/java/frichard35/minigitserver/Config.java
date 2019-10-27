package frichard35.minigitserver;

import org.jboss.logging.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.function.Function;

public class Config {

    private static final Logger LOGGER = Logger.getLogger(Config.class);


    public static final String ENV_PREFIX = "MGS_";
    public static final String DEFAULT_EXTERNAL_CONF_FILE = "config/application.properties";
    public static final String EXTERNAL_CONF_FILE_ENV = ENV_PREFIX + "CONF_FILE";


    private Properties config = new Properties();

    public static Config getDefault() {
        String path = System.getenv(Config.EXTERNAL_CONF_FILE_ENV);
        if (path==null || path.length()==0) {
            path = Config.DEFAULT_EXTERNAL_CONF_FILE;
        }
        return new Config("application-default.properties",path,(k->System.getenv(k)));
    }

    public String getCustomProperties() {
        return customProperties;
    }

    private final String customProperties;


    public Config(String defaultProperties, String customProperties, Function<String, String> getenv) {

        this.customProperties = customProperties;

        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(defaultProperties)) {
            config.load(is);
        } catch (IOException e) {
            throw new IllegalArgumentException("Impossible to load default configuration : " + defaultProperties,e);
        }

        if (Files.exists(Paths.get(customProperties))) {
            try (InputStream is = new FileInputStream(customProperties)) {
                config.load(is);
            } catch (IOException e) {
                throw new IllegalArgumentException("Impossible to load configuration : " + customProperties,e);
            }
        }

        for (Object key : config.keySet()) {
            String envValue = getenv.apply(getEnvKey((String) key));
            if (envValue!=null) {
                config.setProperty((String)key, envValue);
            }
        }
    }

    public String get(String key) {
        return this.config.getProperty(key);
    }

    private String getEnvKey(String key) {
        return ENV_PREFIX + key.replaceAll("\\.","_").toUpperCase();
    }

}
