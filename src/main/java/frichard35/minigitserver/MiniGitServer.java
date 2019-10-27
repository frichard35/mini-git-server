package frichard35.minigitserver;

import frichard35.minigitserver.security.ExternalAuthenticationHandler;
import frichard35.minigitserver.security.GitAccountManager;
import frichard35.minigitserver.security.UserDataStore;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.*;
import org.eclipse.jgit.http.server.GitServlet;
import org.jboss.logging.Logger;

import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.redirect;

public class MiniGitServer{

    private static final Logger LOGGER = Logger.getLogger(MiniGitServer.class);

    public static void main(String[] args) throws ServletException,IOException {

        if (args!=null && args.length == 1 && "user".equals(args[0])) {
            new UserDataStore(Config.getDefault()).createUserInteractively();
            return;
        }

        new MiniGitServer(Config.getDefault());
    }

    private final Undertow server;
    private final Config config;

    public MiniGitServer(Config config) throws ServletException, IOException {
        this.config = config;
        preChecks();
        this.server = this.start();
        welcomeMessage();
    }

    private void preChecks() throws IOException {
        //check that the repos directory is writable
        Path reposPath = Paths.get(config.get("projects.base.path"));
        Files.createDirectories(reposPath);
        Path touchFile = reposPath.resolve(".touch");
        if (Files.notExists(touchFile)) {
            Files.createFile(touchFile);
        }
        Files.setLastModifiedTime(touchFile, FileTime.from(Instant.now()));
    }

    private void welcomeMessage() {
        LOGGER.info("[GLOBAL-SETTINGS]");
        LOGGER.info("Custom configuration        : "+Paths.get(config.getCustomProperties()).toAbsolutePath());
        LOGGER.info("Repositories location       : "+Paths.get(config.get("projects.base.path")).toAbsolutePath());
        LOGGER.info("Global Webhook              : "+config.get("webhook.url"));
        LOGGER.info("[SECURITY-SETTINGS]");
        LOGGER.info("Users configuration         : "+Paths.get(config.get("auth.users.file")).toAbsolutePath());
        LOGGER.info("Basic Authentication        : "+(config.get("auth.enable.basic.auth").equals("true")?"enable":"disable"));
        LOGGER.info("External Authentication     : "+(config.get("auth.enable.external.auth").equals("true")?"enable":"disable"));
        LOGGER.info("Server is ready, sample url : http://"+
                config.get("server.listening.host")+":"+
                config.get("server.listening.port")+
                (config.get("server.context.path").equals("/")?"":config.get("server.context.path").equals("/"))+
                config.get("projects.context.path")+"/your_project.git"
        );
    }

    protected Undertow start () throws ServletException {

        UserDataStore userDataStore = new UserDataStore(config);
        GitAccountManager gitAccountManager = new GitAccountManager(userDataStore);
        LoginConfig loginConfig = new LoginConfig(config.get("auth.realm"));

        boolean enableExternalAuth = Boolean.parseBoolean(config.get("auth.enable.external.auth"));
        boolean enableBasicAuth = Boolean.parseBoolean(config.get("auth.enable.basic.auth"));

        if (!enableBasicAuth && !enableExternalAuth) {
            throw new SecurityException("Cannot Start Mini-git-server: Enable basic auth or external auth.");
        }

        if (enableBasicAuth) {
            loginConfig.addFirstAuthMethod(new AuthMethodConfig("BASIC", Collections.singletonMap("silent", "false")));
        }
        if (enableExternalAuth) {
            loginConfig.addFirstAuthMethod(new AuthMethodConfig("EXTERNAL"));
        }

        ServletInfo homeServlet = Servlets.servlet("HomeServlet", GitServletFactory.HomeServlet.class).addMapping("/");

        Path basePath = Paths.get(config.get("projects.base.path"));
        String projectContextPath = config.get("projects.context.path");
        projectContextPath = (projectContextPath.equals("/")?"":projectContextPath);

        ServletInfo gitServlet = Servlets.servlet("MiniGitServlet", GitServlet.class, new GitServletFactory(config))
                .addInitParam("base-path", basePath.toAbsolutePath().toString())
                .addInitParam("export-all","true")
                .addMapping(projectContextPath+"/*");

        DeploymentInfo deploymentInfo = Servlets.deployment()
                .setClassLoader(MiniGitServer.class.getClassLoader())
                .setContextPath(config.get("server.context.path"))
                .setIdentityManager(gitAccountManager)
                .setLoginConfig(loginConfig)
                .setDeploymentName("mgs.jar")
                .addServlets(homeServlet,gitServlet)
                .addSecurityConstraint(
                    new SecurityConstraint()
                    .addWebResourceCollection(new WebResourceCollection().addUrlPattern("/*"))
                    .addRoleAllowed("committer")
                    .setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.DENY)
                );


        DeploymentManager manager = Servlets.defaultContainer().addDeployment(deploymentInfo);
        manager.deploy();
        HttpHandler servletHandler = manager.start();
        HttpHandler miniGitServerHandler = new ExternalAuthenticationHandler(config,servletHandler);

        PathHandler pathHandler = path(redirect(config.get("server.context.path")))
                                .addPrefixPath(config.get("server.context.path"), miniGitServerHandler);

        Undertow server = Undertow.builder()
                .addHttpListener(Integer.parseInt(config.get("server.listening.port")), config.get("server.listening.host"))
                .setHandler(pathHandler)
                .build();


        server.start();
        return server;
    }

    public void stop() {
        if (this.server != null) {
            this.server.stop();
        }
    }
}
