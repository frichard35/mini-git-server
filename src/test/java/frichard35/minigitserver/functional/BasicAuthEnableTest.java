package frichard35.minigitserver.functional;

import frichard35.minigitserver.Config;

public class BasicAuthEnableTest extends FunctionalTest {

    @Override
    public Config getConfig() {
        Config config = new Config(
                "application-default.properties",
                "src/test/resources/application.properties",
                (k->null));
        return config;
    }

    @Override
    public Boolean expectSuccessfulResult(Req req) {
        if (req == Req.WithBasicAuth_WithoutExternalAuth) {
            return Boolean.TRUE;
        }
        if (req == Req.WithBasicAuth_WithExternalAuth) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }
}
