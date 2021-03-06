package net.openesb.standalone.node.internal;

import com.google.inject.Injector;
import com.sun.jbi.platform.PlatformContext;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import net.openesb.standalone.Constants;
import net.openesb.standalone.Lifecycle;
import net.openesb.standalone.LocalStringKeys;
import net.openesb.standalone.core.CoreModule;
import net.openesb.standalone.env.Environment;
import net.openesb.standalone.env.EnvironmentModule;
import net.openesb.standalone.framework.FrameworkModule;
import net.openesb.standalone.framework.FrameworkService;
import net.openesb.standalone.http.HttpModule;
import net.openesb.standalone.http.HttpServer;
import net.openesb.standalone.inject.ModulesBuilder;
import net.openesb.standalone.jmx.JMXService;
import net.openesb.standalone.jta.TransactionManagerService;
import net.openesb.standalone.jta.TransactionModule;
import net.openesb.standalone.naming.NamingModule;
import net.openesb.standalone.node.Node;
import net.openesb.standalone.plugins.PluginsModule;
import net.openesb.standalone.plugins.PluginsService;
import net.openesb.standalone.settings.Settings;
import net.openesb.standalone.settings.SettingsModule;
import net.openesb.standalone.utils.I18NBundle;
import net.openesb.standalone.utils.ManifestUtils;

/**
 *
 * @author David BRASSELY (brasseld at gmail.com)
 * @author OpenESB Community
 */
public class InstanceNode implements Node {

    private static final Logger LOG =
            Logger.getLogger(InstanceNode.class.getName());
    
    private static final String INSTANCE_NAME = "instance.name";
    private final String nodeName;
    private final Injector injector;
    private final Environment environment;
    private JMXService jMXService;
    private TransactionManagerService tmService;
    private final PluginsService pluginsService;

    public InstanceNode() {
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, I18NBundle.getBundle().getMessage(
                    LocalStringKeys.CONTAINER_INIT_INSTANCE));
        }

        Settings settings = InstanceSettingsPreparer.prepareSettings();
        this.environment = new Environment(settings);
        prepareSystemProperties();
        
        this.pluginsService = new PluginsService(settings, environment);

        ModulesBuilder modules = new ModulesBuilder();

        modules.add(new SettingsModule(settings));
        modules.add(new PluginsModule(settings, pluginsService));
        modules.add(new CoreModule());
        modules.add(new FrameworkModule());
        modules.add(new NamingModule());
        modules.add(new TransactionModule());
        modules.add(new HttpModule());
        modules.add(new NodeModule(this));
        modules.add(new EnvironmentModule(environment));

        injector = modules.createInjector();

        nodeName = settings.get(INSTANCE_NAME,
                Constants.DEFAULT_INSTANCE_NAME);

        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, I18NBundle.getBundle().getMessage(
                    LocalStringKeys.CONTAINER_INIT_INSTANCE_DONE), nodeName);
        }
    }

    @Override
    public void start() {
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, I18NBundle.getBundle().getMessage(
                    LocalStringKeys.CONTAINER_START_INSTANCE), nodeName);
        }

        long startTime = System.currentTimeMillis(); // Get the start Time

        jMXService = injector.getInstance(JMXService.class);
        jMXService.start();

        tmService = injector.getInstance(TransactionManagerService.class);
        tmService.start();
        
        injector.getInstance(FrameworkService.class).start();
        injector.getInstance(HttpServer.class).start();

        for (Class<? extends Lifecycle> plugin : pluginsService.services()) {
            injector.getInstance(plugin).start();
        }
        
        PlatformContext platformContext = injector.getInstance(PlatformContext.class);

        try {
            // Register a management MBean for this framework instance
            ObjectName fwMBeanName = new ObjectName("net.open-esb.standalone",
                    "instance", platformContext.getInstanceName());
            MBeanServer mbs = platformContext.getMBeanServer();
            if (mbs.isRegistered(fwMBeanName)) {
                if (mbs.getAttribute(fwMBeanName, "Loaded").equals(Boolean.TRUE)) {
                    // Framework already loaded from a separate thread/process
                    throw new IllegalStateException("JBI framework instance "
                            + platformContext.getInstanceName() + " has already been loaded");
                } else {
                    // MBean should not be registered - try to clean up
                    mbs.unregisterMBean(fwMBeanName);
                }
            }

            final StandardMBean mbean = new StandardMBean(this, Node.class);
            mbs.registerMBean(mbean, fwMBeanName);

        } catch (Exception e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis(); // Get the end Time

        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, I18NBundle.getBundle().getMessage(
                    LocalStringKeys.CONTAINER_START_INSTANCE_DONE),
                    new Object[]{nodeName, ManifestUtils.getVersion(), (endTime - startTime)});
        }
    }

    @Override
    public void stop() {
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, I18NBundle.getBundle().getMessage(
                    LocalStringKeys.CONTAINER_STOP_INSTANCE), nodeName);
        }

        for (Class<? extends Lifecycle> plugin : pluginsService.services()) {
            injector.getInstance(plugin).stop();
        }
        
        injector.getInstance(HttpServer.class).stop();
        injector.getInstance(FrameworkService.class).stop();
        tmService.stop();
        jMXService.stop();

        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, I18NBundle.getBundle().getMessage(
                    LocalStringKeys.CONTAINER_STOP_INSTANCE_DONE), nodeName);
        }
    }

    @Override
    public String name() {
        return this.nodeName;
    }

    /**
     * This method is preparing the global environement to apply specific
     * options before going further in the runtime.
     */
    private void prepareSystemProperties() {

        if (System.getProperty("java.vendor").equals("IBM Corporation")) {
            /**
             * https://openesb.atlassian.net/browse/OESE-52
             */
            System.setProperty("com.sun.xml.ws.monitoring.endpoint", "false");
            
            /**
             * https://openesb.atlassian.net/browse/OESE-53
             */
            System.setProperty("javax.xml.soap.MessageFactory", "com.sun.xml.internal.messaging.saaj.soap.ver1_1.SOAPMessageFactory1_1Impl");
            System.setProperty("javax.xml.soap.SOAPFactory", "com.sun.xml.internal.messaging.saaj.soap.ver1_1.SOAPFactory1_1Impl");
            System.setProperty("javax.xml.soap.SOAPConnectionFactory", "com.sun.xml.internal.messaging.saaj.client.p2p.HttpSOAPConnectionFactory");
            System.setProperty("javax.xml.soap.MetaFactory", "com.sun.xml.internal.messaging.saaj.soap.SAAJMetaFactoryImpl");
        }
    }
}
