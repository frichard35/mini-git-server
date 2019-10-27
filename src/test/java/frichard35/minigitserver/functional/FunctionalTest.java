package frichard35.minigitserver.functional;

import com.github.tomakehurst.wiremock.WireMockServer;
import frichard35.minigitserver.Config;
import frichard35.minigitserver.MiniGitServer;
import io.undertow.util.StatusCodes;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

public abstract class FunctionalTest {


    public abstract Config getConfig();

    public abstract Boolean expectSuccessfulResult(Req req);

    private static MiniGitServer server;

    //useful for docker hub builds to improve wiremock compatiblity
    private boolean slowEnvironment = ("true".equals(System.getenv("SLOW_ENVIRONMENT")));

    enum Req {
        WithoutBasicAuth_WithoutExternalAuth("http://localhost:8080","http://localhost:8080",null),
        WithoutBasicAuth_WithExternalAuth("http://localhost:8081","http://localhost:8081",null),
        WithBasicAuth_WithoutExternalAuth("http://francois:my_pass_123@localhost:8080","http://localhost:8080","Basic ZnJhbmNvaXM6bXlfcGFzc18xMjM="),
        WithBasicAuth_WithExternalAuth("http://francois:my_pass_123@localhost:8081","http://localhost:8081","Basic ZnJhbmNvaXM6bXlfcGFzc18xMjM=");

        String gitUrl;
        String url;
        String authzHeader;
        Req(String gitUrl,String url,String authzHeader) {
            this.gitUrl = gitUrl;
            this.url = url;
            this.authzHeader = authzHeader;
        }

        public String getGitUrl() {
            return gitUrl;
        }

        public String getUrl() {
            return url;
        }

        public String getAuthzHeader() {
            return authzHeader;
        }
    }

    WireMockServer wireMockServer;

    @BeforeEach
    public void setup() throws Exception {
        //prepare mini-git-server
        server = new MiniGitServer(getConfig());

        //prepare wiremock (client and server)
        wireMockServer = new WireMockServer(8081);
        wireMockServer.start();
        configureFor("localhost", 8081);

        if (slowEnvironment) {
            System.err.println("SLOW ENVIRONMENT DETECTED");
            Thread.sleep(1000);
        }
    }

    @AfterEach
    public void teardown() throws Exception {
        wireMockServer.stop();
        server.stop();
    }

    @Test
    public void testHomePage_WithoutBasicAuth_WithoutExternalAuth() throws Exception {
        testHomePage(Req.WithoutBasicAuth_WithoutExternalAuth);
    }

    @Test
    public void testHomePage_WithBasicAuth_WithoutExternalAuth() throws Exception {
        testHomePage(Req.WithBasicAuth_WithoutExternalAuth);
    }

    @Test
    public void testHomePage_WithoutBasicAuth_WithExternalAuth() throws Exception {
        testHomePage(Req.WithoutBasicAuth_WithExternalAuth);
    }

    @Test
    public void testHomePage_WithBasicAuth_WithExternalAuth() throws Exception {
        testHomePage(Req.WithBasicAuth_WithExternalAuth);
    }

    public void testHomePage(Req req) throws Exception {
        removeAllMappings();
        stubFor(any(urlMatching(".*"))
                .willReturn(aResponse()
                        .proxiedFrom("http://localhost:8080")
                        .withAdditionalRequestHeader("X-Forwarded-User", "francois")));

        Boolean shouldSucceed = expectSuccessfulResult(req);
        try {
            Request get = Request.Get(req.getUrl());
            if (req.getAuthzHeader()!=null) {
                get = get.addHeader("Authorization", req.getAuthzHeader());
            }
            String response = get.execute().returnContent().asString();
            if (shouldSucceed) {
                assertThat(response).startsWith("mini-git-server v");
            } else {
                fail("url: %s should failed, get response %s", req.toString(), response);
            }
        } catch (HttpResponseException e) {
            if (shouldSucceed) {
                fail("Should succeeded");
            } else {
                assertThat(e.getStatusCode()).isIn(StatusCodes.UNAUTHORIZED,StatusCodes.FORBIDDEN);
            }
        }
    }

    @Test
    public void testPush_WithBasicAuth_WithoutExternalAuth() throws Exception {
        testPush(Req.WithBasicAuth_WithoutExternalAuth);
    }

    @Test
    public void testPush_WithoutBasicAuth_WithoutExternalAuth() throws Exception {
        testPush(Req.WithoutBasicAuth_WithoutExternalAuth);
    }

    @Test
    public void testPush__WithBasicAuth__WithExternalAuth() throws Exception {
        testPush(Req.WithBasicAuth_WithExternalAuth);
    }

    @Test
    public void testPush__WithoutBasicAuth__WithExternalAuth() throws Exception {
        testPush(Req.WithoutBasicAuth_WithExternalAuth);
    }

    private void testPush(Req req) throws Exception {
        Path projectPath = prepareRepoForPush();
        Boolean shouldSucceed = expectSuccessfulResult(req);
        execute("git remote set-url origin "+req.getGitUrl()+"/git/project1.git", projectPath, true);
        execute("git -c credential.helper= push", projectPath, shouldSucceed);

        if (slowEnvironment) {
            Thread.sleep(1000);
        } else {
            Thread.sleep(100);
        }
        verify(exactly(shouldSucceed?1:0), getRequestedFor(urlEqualTo("/webhook/project1?master")));
    }

    private Path prepareRepoForPush() throws IOException {

        //prepare stubs
        stubFor(get(urlEqualTo("/webhook/project1?master"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/plain")
                        .withBody("OK")));

        stubFor(any(urlMatching("/git/.*"))
                .willReturn(aResponse()
                        .proxiedFrom("http://localhost:8080")
                        .withAdditionalRequestHeader("X-Forwarded-User", "francois")));

        //prepare repo
        Path basedir = Paths.get("target/base/").toAbsolutePath();
        Path workdir = Paths.get("target/work/").toAbsolutePath();
        Files.createDirectories(workdir);
        execute("rm -rf " + workdir + "/project1", workdir, true);
        execute("rm -rf " + basedir, workdir, true);
        execute("mkdir -p " + basedir + "/project1.git", workdir, true);
        execute("git init --bare  --shared=group " + basedir + "/project1.git", workdir, true);
        execute("git clone \"" + basedir + "/project1.git\"", workdir, true);

        Path projectPath = workdir.resolve("project1/");
        execute("git config user.email you@example.com", projectPath, true);
        execute("git config user.name Your_Name", projectPath, true);

        execute("touch test", projectPath, true);
        execute("git add test", projectPath, true);
        execute("git commit -m \"first commit\"", projectPath, true);

        return projectPath;
    }

    private void execute(String command, Path workdir, boolean shouldSucceed) throws IOException {
        CommandLine cmdLine = CommandLine.parse(command);
        DefaultExecutor executor = new DefaultExecutor();
        executor.setWorkingDirectory(workdir.toFile());
        int exitValue;
        try {
            exitValue = executor.execute(cmdLine, Collections.singletonMap("GIT_TERMINAL_PROMPT", "0"));
        } catch (ExecuteException e) {
            exitValue = e.getExitValue();
        }
        if (shouldSucceed) {
            assertThat(exitValue).as("The command has not succeeded : %s", command).isEqualTo(0);
        } else {
            assertThat(exitValue).as("The command has not failed : %s", command).isGreaterThan(0);
        }
    }
}
