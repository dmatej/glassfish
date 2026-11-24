package arquillian;

import com.sun.ts.tests.ejb30.bb.session.stateless.annotation.enventry.Client;

import java.lang.System.Logger;
import java.net.URL;

import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import tck.arquillian.porting.lib.spi.AbstractTestArchiveProcessor;

import static java.lang.System.Logger.Level.INFO;

public class GlassFishXmlProcessor extends AbstractTestArchiveProcessor {

    private static final Logger LOG = System.getLogger(GlassFishXmlProcessor.class.getName());

    @Override
    public void processClientArchive(JavaArchive clientArchive, Class<?> testClass, URL sunXmlURL) {
       if (testClass.isAssignableFrom(Client.class)) {
           clientArchive.delete("META-INF/sun-application-client.xml");
           URL resURL = Client.class.getResource("/ejb30/bb/session/stateless/annotation/enventry/ejb3_bb_stateless_enventry_client.glassfish-application-client.xml");
           clientArchive.addAsManifestResource(resURL, "glassfish-application-client.xml");
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
