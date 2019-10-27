package frichard35.minigitserver.security;

import frichard35.minigitserver.Config;
import org.jboss.logging.Logger;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.management.openmbean.KeyAlreadyExistsException;
import java.io.Console;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.util.Base64;
import java.util.Properties;

/**
 * Class which manage the users and their password
 */
public class UserDataStore {

    private static final Logger LOGGER = Logger.getLogger(UserDataStore.class);

    private static final SecureRandom RAND = new SecureRandom();
    private static final int ITERATIONS = 4096;
    private static final int KEY_LENGTH = 512;
    private static final int SALT_LENGTH = 16;
    public static final String CREDENTIALS_SEPARATOR = ",";

    private static final String ALGORITHM = "PBKDF2WithHmacSHA512";
    private final Config config;
    private Properties usersCredentials;
    private Instant storeFileInstant;

    public UserDataStore(Config config) {
        this.config = config;
        loadUsersFile();
    }

    public void createUserInteractively() {
        Console console = System.console();
        if (console == null) {
            System.out.println("Couldn't get Console instance");
            System.exit(0);
        }
        console.printf("# New user creation (Password used for basic authentication only)\n");
        console.printf("# Users file : " + Paths.get(config.get("auth.users.file")).toAbsolutePath() + "\n");
        String username = console.readLine("Enter your username: ");
        String password = console.readLine("Enter your password: ");

        //hide the password
        int count = 1;
        console.printf(String.format("\033[%dA",count)); // Move up
        console.printf("\033[2K"); // Erase line content
        console.printf("Enter your password: ");
        password.chars().forEach(c ->
                console.printf("#")
                );
        console.printf("\n");

        long start = System.currentTimeMillis();
        try {
            createUser(username, password, false);
        } catch (KeyAlreadyExistsException e) {
            console.printf("User already exists, press any key to overwrite the password or CTRL-C to cancel.");
            console.readLine();
            start = System.currentTimeMillis();
            createUser(username, password, true);
        }
        console.printf("Done in "+(System.currentTimeMillis()-start)+"ms\n");
    }

    public void createUser(String username, String password, boolean force) throws KeyAlreadyExistsException {

        if (!force && usersCredentials.containsKey(username)) {
            throw new KeyAlreadyExistsException();
        }

        String salt = generateSalt(SALT_LENGTH);
        String hashPassword = hashPassword(password,salt);
        usersCredentials.put(username, salt + CREDENTIALS_SEPARATOR + hashPassword);
        saveUsersFile();

    }

    private void loadUsersFile() {
        Properties properties = new Properties();
        Path usersFile = Paths.get(config.get("auth.users.file"));
        if (Files.exists(usersFile)) {
            try (FileInputStream is = new FileInputStream(usersFile.toFile())) {
                properties.load(is);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }

        } else {
            try {
                Files.createFile(usersFile);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }

        }
        storeFileInstant = getLastModifiedTime();
        this.usersCredentials = properties;
    }

    private Instant getLastModifiedTime() {
        try {
            return Files.getLastModifiedTime(Paths.get(config.get("auth.users.file"))).toInstant();
        } catch (IOException e) {
            throw new RuntimeException("Impossible to read LastModifiedTime of users file.", e);
        }
    }

    private void saveUsersFile() {
        Path usersFile = Paths.get(config.get("auth.users.file"));
        try {
            Files.createDirectories(usersFile.getParent());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        try (FileOutputStream os = new FileOutputStream(usersFile.toFile())) {
            usersCredentials.store(os,"Users file");
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String getUserCredentials(String user) {
        checkForModifications();
        return usersCredentials.getProperty(user);
    }

    protected String generateSalt (final int length) {

        if (length < 1) {
            throw new IllegalArgumentException("error in generateSalt: length must be > 0");
        }

        byte[] salt = new byte[length];
        RAND.nextBytes(salt);

        return Base64.getEncoder().encodeToString(salt);
    }

    protected String hashPassword (String password, String salt) {

        char[] chars = password.toCharArray();
        byte[] bytes = salt.getBytes();

        PBEKeySpec spec = new PBEKeySpec(chars, bytes, ITERATIONS, KEY_LENGTH);

        try {
            SecretKeyFactory fac = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] securePassword = fac.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(securePassword);

        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalArgumentException("Exception encountered in hashPassword", ex);
        } finally {
            spec.clearPassword();
        }
    }

    public boolean verifyPassword (String suppliedPassword, String storedSalt, String storedHashPassword) {
        String suppliedHashPassword = hashPassword(suppliedPassword, storedSalt);
        return suppliedHashPassword.equals(storedHashPassword);
    }

    private void checkForModifications() {
        if (getLastModifiedTime().isAfter(storeFileInstant)) {
            loadUsersFile();
            LOGGER.info("Users refresh from file.");
        }
    }
}
