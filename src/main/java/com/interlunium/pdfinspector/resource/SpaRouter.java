package com.interlunium.pdfinspector.resource;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Serves {@code index.html} for client side routes that are not API endpoints,
 * so that deep links such as {@code /viewer/{id}} work with the Vue Router
 * history mode when the built frontend is bundled inside the Quarkus application.
 */
@ApplicationScoped
public class SpaRouter {

    private static final Logger LOG = LoggerFactory.getLogger(SpaRouter.class);

    private final Router router;

    /**
     * @param router the Vert.x router
     */
    @Inject
    public SpaRouter(Router router) {
        this.router = router;
    }

    void register(@Observes io.quarkus.runtime.StartupEvent event) {
        router.get().handler(ctx -> {
            String path = ctx.request().path();
            if (path.startsWith("/api") || path.equals("/q")) {
                ctx.next();
                return;
            }
            if (path.contains(".")) {
                ctx.next();
                return;
            }
            byte[] indexHtml = loadIndexHtml();
            if (indexHtml == null) {
                ctx.next();
                return;
            }
            ctx.response()
                    .putHeader("Content-Type", "text/html;charset=UTF-8")
                    .end(Buffer.buffer(indexHtml));
        });
    }

    private byte[] loadIndexHtml() {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/resources/index.html")) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            LOG.warn("Unable to read index.html for SPA fallback", e);
            return null;
        }
    }
}
