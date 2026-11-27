package arquillian;

import com.sun.ts.tests.ejb30.bb.session.stateless.annotation.enventry.Client;

import java.lang.System.Logger;
import java.net.URL;

import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import tck.arquillian.porting.lib.spi.AbstractTestArchiveProcessor;

import static java.lang.System.Logger.Level.INFO;

public class GlassFishXmlProcessor extends AbstractTestArchiveProcessor {

    private static final Logger LOG = System.getLogger(GlassFishXmlProcessor.class.getName());
    private static final String PATH_GF_APPLICATION_CLIENT_URL = "/ejb30/bb/session/stateless/annotation/enventry/ejb3_bb_stateless_enventry_client.glassfish-application-client.xml";
    private static final String PATH_SUN_APPLICATION_CLIENT_XML = "META-INF/sun-application-client.xml";

    @Override
    public void processClientArchive(JavaArchive clientArchive, Class<?> testClass, URL sunXmlURL) {
        if (testClass.isAssignableFrom(Client.class)
            && "ejb3_bb_stateless_enventry_client.jar".equals(clientArchive.getName())) {
            Node node = clientArchive.delete(PATH_SUN_APPLICATION_CLIENT_XML);
           if (node == null) {
               throw new IllegalStateException(
                   "Failed to update  " + clientArchive.getName() + ", because it did not contain the "
                       + PATH_SUN_APPLICATION_CLIENT_XML + " file. Contents: \n" + clientArchive.toString(true));
           }
           URL xmlURL = Client.class.getResource(PATH_GF_APPLICATION_CLIENT_URL);
           if (xmlURL == null) {
               throw new IllegalStateException(
                   "Failed to update  " + clientArchive.getName() + ", because it did not contain the "
                       + PATH_SUN_APPLICATION_CLIENT_XML + " file. Contents: \n" + clientArchive.toString(true));
           }
           clientArchive.addAsManifestResource(xmlURL, "glassfish-application-client.xml");
           LOG.log(INFO, () -> "Updated " + clientArchive.getName() + " Contents: \n" + clientArchive.toString(true));
       }
    }

    @Override
    public void processEjbArchive(JavaArchive ejbArchive, Class<?> testClass, URL sunXmlUrl) {
    }

    @Override
    public void processWebArchive(WebArchive webArchive, Class<?> testClass, URL sunXmlUrl) {
    }

    @Override
    public void processRarArchive(JavaArchive rarArchive, Class<?> testClass, URL sunXmlUrl) {
    }

    @Override
    public void processParArchive(JavaArchive parArchive, Class<?> testClass, URL persistenceXmlUrl) {
    }

    @Override
    public void processEarArchive(EnterpriseArchive earArchive, Class<?> testClass, URL sunXmlUrl) {
    }

}
