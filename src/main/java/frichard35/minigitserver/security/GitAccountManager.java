package frichard35.minigitserver.security;

import io.undertow.security.idm.*;
import org.jboss.logging.Logger;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static frichard35.minigitserver.security.UserDataStore.CREDENTIALS_SEPARATOR;

public class GitAccountManager implements IdentityManager {

    private static final Logger LOGGER = Logger.getLogger(GitAccountManager.class);

    private final UserDataStore userDataStore;

    public GitAccountManager(UserDataStore userDataStore) {
        this.userDataStore = userDataStore;
    }

    private Account loadAccountFromStore(String id) {
        String credentialsLine = userDataStore.getUserCredentials(id);
        if (credentialsLine==null) {
            throw new SecurityException("User '" + id + "' not exists.");
        }
        String[] credentials = credentialsLine.split(CREDENTIALS_SEPARATOR);
        if (credentials.length!=2) {
            throw new SecurityException("Corrupt credentials for " + id);
        }
        return new GitAccount(id, credentials[1].toCharArray(), credentials[0],"committer");
    }

    @Override
    public Account verify(Account account) {
        // Just re-use the existing account.
        return account;
    }

    @Override
    public Account verify(String id, Credential credential) {
        Account account = loadAccountFromStore(id);
        if (account != null && verifyCredential(account, credential)) {
            return account;
        }

        return null;
    }

    @Override
    public Account verify(Credential credential) {
        return null;
    }

    private boolean verifyCredential(Account account, Credential credential) {

        if (!(account instanceof GitAccount)) {
            LOGGER.warn("Manage only " + GitAccount.class.getName() + " not " + account.getClass().getName());
            return false;
        }

        if (!(credential instanceof PasswordCredential) && !(credential instanceof ExternalCredential)) {
            LOGGER.warn("Manage only " + PasswordCredential.class.getName() + " or " +
                        ExternalCredential.class.getName() + "+ not " + credential.getClass().getName());
            return false;
        }

        if (credential instanceof PasswordCredential) {
            String storedPassword = new String(((GitAccount) account).password);
            String storedSalt = ((GitAccount) account).salt;
            String suppliedPassword = new String(((PasswordCredential) credential).getPassword());
            return userDataStore.verifyPassword(suppliedPassword,storedSalt,storedPassword);
        } else if (credential instanceof ExternalCredential) {
            return true;
        }
        return false;
    }

    private static class GitAccount implements Account {

        private final GitPrincipal principal;
        private final char[] password;
        private final String salt;
        private final String[] roles;

        private GitAccount(String name, char[] password, String salt, String... roles) {
            this.principal = new GitPrincipal(name);
            this.password = password;
            this.salt = salt;
            this.roles = roles;
        }

        @Override
        public Principal getPrincipal() {
            return principal;
        }

        @Override
        public Set<String> getRoles() {
            return new HashSet<>(Arrays.asList(roles));
        }
    }

    private static class GitPrincipal implements Principal {

        private final String name;

        private GitPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

    }


}