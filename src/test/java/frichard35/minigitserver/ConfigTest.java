package frichard35.minigitserver;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigTest {

    private Config config = new Config(
            "application-test-default.properties",
            "src/test/resources/application-test.properties",
            key -> {
                if (key.equals("MGS_KEY3")) {
                    return "specific_value3";
                }
                if (key.equals("MGS_KEY4")) {
                    return "specific_value4";
                }
                return null;
            });

    @Test
    public void confFromDefault() {
        assertThat(config.get("key1")).isEqualTo("value1");
    }

    @Test
    public void confFromFile() {
        assertThat(config.get("key2")).isEqualTo("specific_value2");
    }

    @Test
    public void confFromEnv() {
        assertThat(config.get("key3")).isEqualTo("specific_value3");
    }

    @Test
    public void confFromEnv2() {
        assertThat(config.get("key4")).isEqualTo("specific_value4");
    }
}
