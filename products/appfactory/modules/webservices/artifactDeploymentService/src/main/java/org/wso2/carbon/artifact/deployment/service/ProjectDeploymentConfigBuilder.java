package org.wso2.carbon.artifact.deployment.service;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ProjectDeploymentConfigBuilder {
    private static final Log log = LogFactory.getLog(ProjectDeploymentConfigBuilder.class);

    public static ProjectDeploymentConfiguration createDeploymentConfiguration()
            throws ProjectDeploymentExceptions {
        OMElement deploymentConfig = loadConfigXML();
        ProjectDeploymentConfiguration configuration = new ProjectDeploymentConfiguration();

        if (deploymentConfig != null) {
            if (!deploymentConfig.getQName().equals(
                    new QName(ProjectDeploymentConstants.PROJECT_DEPLOYMENT_CONFIG_ROOT_ELEMENT))) {
                throw new ProjectDeploymentExceptions("Invalid root element in deployment configuration");
            }

            OMElement svnBaseURLElement = deploymentConfig.getFirstChildWithName(new QName(
                    ProjectDeploymentConstants.PROJECT_DEPLOYMENT_CONFIG_SVN_URL));
            if (svnBaseURLElement == null) {
                throw new ProjectDeploymentExceptions("SVN base url is not defined in deployment configuration.");
            }
            configuration.setSvnBaseURL(svnBaseURLElement.getText());

            Iterator stagesItr = deploymentConfig.getChildrenWithName(new QName(
                    ProjectDeploymentConstants.PROJECT_DEPLOYMENT_CONFIG_STAGE));
            if (!stagesItr.hasNext()) {
                throw new ProjectDeploymentExceptions("No stages defined for deployment of artifacts in deployment configuration.");
            }
            while (stagesItr.hasNext()) {
                OMElement stageElement = (OMElement) stagesItr.next();
                String stageName = stageElement.getAttributeValue(new QName("name")).trim();
                Iterator deploymentServerLocationsItr = stageElement.getChildrenWithName(new QName(ProjectDeploymentConstants.PROJECT_DEPLOYMENT_CONFIG_SERVER_LOCATION));
                List<String> deploymentServerLocations = new ArrayList<String>();
                while (deploymentServerLocationsItr.hasNext()) {
                    OMElement deploymentServerLocationElement = (OMElement) deploymentServerLocationsItr.next();
                    deploymentServerLocations.add(deploymentServerLocationElement.getText().trim());
                }
                configuration.addDeploymentServerLocations(stageName, deploymentServerLocations);
            }

        }
        return configuration;

    }

    private static OMElement loadConfigXML() throws ProjectDeploymentExceptions {

        String carbonHome = System.getProperty("carbon.config.dir.path");
        String path = carbonHome + File.separator + ProjectDeploymentConstants.PROJECT_DEPLOYMENT_CONFIG_FILE_NAME;

        BufferedInputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(new File(path)));
            XMLStreamReader parser = XMLInputFactory.newInstance().
                    createXMLStreamReader(inputStream);
            StAXOMBuilder builder = new StAXOMBuilder(parser);
            OMElement omElement = builder.getDocumentElement();
            omElement.build();
            return omElement;
        } catch (FileNotFoundException e) {
            String errorMessage = ProjectDeploymentConstants.PROJECT_DEPLOYMENT_CONFIG_FILE_NAME
                                  + "cannot be found in the path : " + path;
            log.error(errorMessage, e);
            throw new ProjectDeploymentExceptions(errorMessage, e);
        } catch (XMLStreamException e) {
            String errorMessage = "Invalid XML for " + ProjectDeploymentConstants.PROJECT_DEPLOYMENT_CONFIG_FILE_NAME
                                  + " located in the path : " + path;
            log.error(errorMessage, e);
            throw new ProjectDeploymentExceptions(errorMessage, e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                String errorMessage = "Can not close the input stream";
                log.error(errorMessage, e);
            }
        }
    }

}