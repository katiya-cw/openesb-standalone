package net.openesb.standalone.http.grizzly;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.ws.rs.ext.RuntimeDelegate;
import net.openesb.rest.api.OpenESBApplication;
import net.openesb.standalone.LifecycleException;
import net.openesb.standalone.LocalStringKeys;
import net.openesb.standalone.env.Environment;
import net.openesb.standalone.http.HttpServer;
import net.openesb.standalone.http.handlers.AdminConsoleHandler;
import net.openesb.standalone.http.handlers.Handler;
import net.openesb.standalone.http.handlers.SitePluginHandler;
import net.openesb.standalone.settings.Settings;
import net.openesb.standalone.utils.I18NBundle;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.jersey.server.ContainerFactory;

/**
 *
 * @author David BRASSELY (brasseld at gmail.com)
 * @author OpenESB Community
 */
public class EmbeddedHttpServer implements HttpServer {
    
    private static final Logger LOG =
            Logger.getLogger(HttpServer.class.getPackage().getName());
    private static final String HTTP_LISTENER_NAME = "openesb-http-server";
    private static final String HTTP_PORT_PROPERTY = "http.port";
    private static final String HTTP_ENABLED_PROPERTY = "http.enabled";
    private static final String HTTP_BINDING_PROPERTY = "http.binding";
    private static final int DEFAULT_HTTP_PORT = 4848;
    private static final boolean DEFAULT_HTTP_ENABLED = true;
    private org.glassfish.grizzly.http.server.HttpServer httpServer = null;
    
    private final Settings settings;
    private final Environment environment;

    @Inject
    public EmbeddedHttpServer(Settings settings, Environment environment) {
        this.settings = settings;
        this.environment = environment;
    }

    @Override
    public void start() throws LifecycleException {
        boolean enabled = settings.getAsBoolean(HTTP_ENABLED_PROPERTY, DEFAULT_HTTP_ENABLED);

        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, I18NBundle.getBundle().getMessage(
                    LocalStringKeys.HTTP_SERVER_ENABLED, enabled));
        }

        if (enabled) {
            httpServer = createHttpServer();

            // Map the path to the processor.
            final ServerConfiguration config = httpServer.getServerConfiguration();

            AdminConsoleHandler consoleHandler = new AdminConsoleHandler();
            config.addHttpHandler(consoleHandler.getHandler(), consoleHandler.path());
            
            SitePluginHandler pluginHandler = new SitePluginHandler(environment);
            config.addHttpHandler(pluginHandler.getHandler(), pluginHandler.path());
            
            /*
             * OESE-46
             * https://openesb.atlassian.net/browse/OESE-46
             * 
             * Change the JAX-RS runtime delegate before initializing the API
             * handler if it is already instanciated.
             * It implies to use the correct TCCL for the RuntimeDelegateImpl.
             * 
             * WARNING: This is a temporary fix before moving REST-BC component
             * to JAX-RS 2.
             */
            RuntimeDelegate.setInstance(null);
            
            HttpHandler handler = ContainerFactory.createContainer(HttpHandler.class, new OpenESBApplication());
            config.addHttpHandler(handler, "/api");

            try {
                if (LOG.isLoggable(Level.INFO)) {
                    LOG.log(Level.INFO, I18NBundle.getBundle().getMessage(
                            LocalStringKeys.HTTP_START_SERVER));
                }

                httpServer.start();
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, I18NBundle.getBundle().getMessage(
                        LocalStringKeys.HTTP_START_SERVER_FAILED), ex);
            }
        }
    }

    @Override
    public void stop() throws LifecycleException {
        if (httpServer != null) {
            httpServer.shutdownNow();
            if (LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, I18NBundle.getBundle().getMessage(
                        LocalStringKeys.HTTP_STOP_SERVER));
            }
        }
    }

    private org.glassfish.grizzly.http.server.HttpServer createHttpServer() {
        int port = settings.getAsInt(HTTP_PORT_PROPERTY, DEFAULT_HTTP_PORT);
        String binding = settings.get(HTTP_BINDING_PROPERTY,
                NetworkListener.DEFAULT_NETWORK_HOST);

        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, I18NBundle.getBundle().getMessage(
                    LocalStringKeys.HTTP_SERVER_PORT, port));
        }

        final org.glassfish.grizzly.http.server.HttpServer server = 
                new org.glassfish.grizzly.http.server.HttpServer();
        final NetworkListener listener = new NetworkListener(HTTP_LISTENER_NAME,
                binding, port);

        ThreadPoolConfig threadPoolConfig = ThreadPoolConfig
                .defaultConfig()
                .setCorePoolSize(5)
                .setMaxPoolSize(5);

        listener.getTransport().setWorkerThreadPoolConfig(threadPoolConfig);
        /*
         listener.setSecure(secure);
         if (sslEngineConfigurator != null) {
         listener.setSSLEngineConfig(sslEngineConfigurator);
         }
         */

        server.addListener(listener);
        return server;
    }
}
