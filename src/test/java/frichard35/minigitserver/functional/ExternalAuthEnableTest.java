package frichard35.minigitserver.functional;

import frichard35.minigitserver.Config;

public class ExternalAuthEnableTest extends FunctionalTest {

    @Override
    public Config getConfig() {
        Config config = new Config(
                "application-default.properties",
                "src/test/resources/application.properties",
                (k->{
                    if (k.equals("MGS_AUTH_ENABLE_EXTERNAL_AUTH"))
                        return "true";
                    if (k.equals("MGS_AUTH_ENABLE_BASIC_AUTH"))
                        return "false";
                    return null;
                }));
        return config;
    }

    @Override
    public Boolean expectSuccessfulResult(Req req) {
        if (req == Req.WithoutBasicAuth_WithExternalAuth) {
            return Boolean.TRUE;
        }
        if (req == Req.WithBasicAuth_WithExternalAuth) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }
}
