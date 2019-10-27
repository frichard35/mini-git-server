package frichard35.minigitserver.functional;

import frichard35.minigitserver.Config;

public class BasicAndExternalAuthEnableTest extends FunctionalTest {

    @Override
    public Config getConfig() {
        Config config = new Config(
                "application-default.properties",
                "src/test/resources/application.properties",
                (k->{
                    if (k.equals("MGS_AUTH_ENABLE_EXTERNAL_AUTH"))
                        return "true";
                    return null;
                }));
        return config;
    }

    @Override
    public Boolean expectSuccessfulResult(Req req) {
        if (req == Req.WithoutBasicAuth_WithoutExternalAuth) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }
}
