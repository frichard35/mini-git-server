package frichard35.minigitserver;

import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.util.ImmediateInstanceHandle;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.jboss.logging.Logger;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provide the GitServlet From JGit (+ the webhook capability)
 */
public class GitServletFactory implements InstanceFactory<GitServlet> {

    private static final Logger LOGGER = Logger.getLogger(GitServletFactory.class);

    private final Config config;

    public GitServletFactory(Config config) {
        this.config = config;
    }

    @Override
    public InstanceHandle<GitServlet> createInstance() throws InstantiationException {
        GitServlet servlet = new GitServlet();

        servlet.addReceivePackFilter(new Filter() {
            @Override
            public void init(FilterConfig filterConfig) throws ServletException {

            }

            @Override
            public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
                ReceivePack rp = (ReceivePack) servletRequest.getAttribute(ServletUtils.ATTRIBUTE_HANDLER);

                Map<String, String> branchesBefore = branches(rp.getRepository());
                filterChain.doFilter(servletRequest, servletResponse);
                Map<String, String> branchesAfter = branches(rp.getRepository());

                //compares branches before and after
                for (Map.Entry<String, String> branchAfter : branchesAfter.entrySet()) {
                    if (!branchAfter.getValue().equals(branchesBefore.get(branchAfter.getKey()))) {
                        if (branchAfter.getKey().startsWith("refs/heads/")) {
                            sendWebhook(rp.getRepository(), branchAfter.getKey().substring(11));
                        }
                    }
                }
            }

            public void sendWebhook(Repository repository, String branch) {
                String repo = null;
                try {
                    String webhookUrl = config.get("webhook.url");
                    repo = repository.getDirectory().getName();
                    if (repo.endsWith(".git")) {
                        repo = repo.substring(0, repo.length() - 4);
                    }
                    webhookUrl = webhookUrl.replaceAll("%PROJECT%",repo);
                    webhookUrl = webhookUrl.replaceAll("%BRANCH%",branch);
                    int socketTimeout = Integer.parseInt(config.get("webhook.socket.timeout"));
                    int connectTimeout = Integer.parseInt(config.get("webhook.connect.timeout"));
                    RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(socketTimeout).setConnectTimeout(connectTimeout).build();
                    HttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

                    HttpGet get = new HttpGet(webhookUrl);
                    LOGGER.info("Webook event on : " + get.getURI());
                    httpClient.execute(get);
                } catch (Exception e) {
                    //non blocking
                    LOGGER.error("Exception when executing webhook for " + repo + " and branch " + branch, e);
                }
            }

            protected Map<String, String> branches(Repository repository) throws IOException {
                List<Ref> branchesRef = null;
                try {
                    branchesRef = new Git(repository).branchList().call();
                } catch (GitAPIException e) {
                    LOGGER.error("Error when retrieving git branches", e);
                }
                Map<String, String> branches = new HashMap<>();
                for (Ref branch : branchesRef) {
                    branches.put(branch.getName(), branch.getObjectId() == null ? "" : branch.getObjectId().getName());
                }
                return branches;

            }

            @Override
            public void destroy() {

            }
        });
        return new ImmediateInstanceHandle(servlet);
    }

    public static class HomeServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            PrintWriter writer = resp.getWriter();
            writer.println("mini-git-server v" + HomeServlet.class.getPackage().getImplementationVersion());
        }
    }
}
