package frichard35.minigitserver.security;

import frichard35.minigitserver.Config;
import io.undertow.security.impl.ExternalAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;


public class ExternalAuthenticationHandler implements HttpHandler {

    private final HttpHandler next;

    private Config config;

    public ExternalAuthenticationHandler(final Config config, final HttpHandler next) {
        this.next = next;
        this.config = config;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        String user = exchange.getRequestHeaders().getFirst(config.get("auth.external.request.header"));
        if (user!=null && !user.equals("")) {
            exchange.putAttachment(ExternalAuthenticationMechanism.EXTERNAL_PRINCIPAL, user);
            exchange.putAttachment(ExternalAuthenticationMechanism.EXTERNAL_AUTHENTICATION_TYPE, "ExternalAuth");
        }

        next.handleRequest(exchange);
    }

    public HttpHandler getNext() {
        return next;
    }


}
