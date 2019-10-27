package frichard35.minigitserver;

import frichard35.minigitserver.security.UserDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.openmbean.KeyAlreadyExistsException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

public class UserDataStoreTest {

    private static Config config;

    @BeforeEach
    public void setup() throws Exception {

        config = new Config(
                "application-default.properties",
                "src/test/resources/application.properties",(k->{
                        if (k.equals("MGS_AUTH_USERS_FILE")) {
                            return "target/users.properties";
                        } else {
                            return null;
                        }
                    }
                ));
    }

    @Test
    public void testWithoutForcing() throws IOException {
        Files.deleteIfExists(Paths.get(config.get("auth.users.file")));
        UserDataStore manager = new UserDataStore(config);
        manager.createUser("toto", "titi", false);
        try {
            manager.createUser("toto", "titi2", false);
            fail("should fail");
        } catch (KeyAlreadyExistsException e ) {

        }
    }

    @Test
    public void testWithForcing() throws IOException {
        Files.deleteIfExists(Paths.get(config.get("auth.users.file")));
        UserDataStore manager = new UserDataStore(config);
        manager.createUser("toto", "titi", true);
        String credentials = manager.getUserCredentials("toto");
        manager.createUser("toto", "titi2", true);
        assertThat(manager.getUserCredentials("toto")).isNotEqualTo(credentials);
    }

    @Test
    public void testValidate() throws IOException {
        Files.deleteIfExists(Paths.get(config.get("auth.users.file")));
        UserDataStore manager = new UserDataStore(config);
        manager.createUser("toto", "titi", true);
        String[] credentials = manager.getUserCredentials("toto").split(",");
        assertThat(credentials).hasSize(2);

        assertThat(manager.verifyPassword("titi",credentials[0],credentials[1])).isTrue();
        assertThat(manager.verifyPassword("titia",credentials[0],credentials[1])).isFalse();
        assertThat(manager.verifyPassword("titi",credentials[0]+"a",credentials[1])).isFalse();
        assertThat(manager.verifyPassword("titi",credentials[0],credentials[1]+"a")).isFalse();
    }
}
