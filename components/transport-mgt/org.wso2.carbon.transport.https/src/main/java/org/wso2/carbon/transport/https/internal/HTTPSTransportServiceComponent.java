/*
 * Copyright (c) 2006, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.transport.https.internal;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.core.transports.TransportService;
import org.wso2.carbon.transport.https.HTTPSTransportService;
import org.wso2.carbon.utils.ConfigurationContextService;

/**
 * @scr.component name="https.transport.services" immediate="true"
 * @scr.reference name="config.context.service" interface="org.wso2.carbon.utils.ConfigurationContextService"
 * cardinality="1..1" policy="dynamic"  bind="setConfigurationContextService" unbind="unsetConfigurationContextService"
 */
public class HTTPSTransportServiceComponent {

    private static Log log = LogFactory.getLog(HTTPSTransportServiceComponent.class);
    private ConfigurationContextService contextService;

    public HTTPSTransportServiceComponent() {
    }

    protected void activate(ComponentContext ctxt) {
        log.debug("******* HTTPS Transport bundle is activated ******* ");
        //Properties props = new Properties();
		HTTPSTransportService httpsTransport;
		ConfigurationContext configContext;

        if (log.isDebugEnabled()) {
			log.debug("Starting the http transport component ...");
		}

        try {

            if (contextService != null) {
                // Getting server's configContext instance
                configContext = contextService.getServerConfigContext();
            } else {
                throw new Exception("ConfigurationContext is not found while loading " +
                        "org.wso2.carbon.transport.https bundle");
            }

            // Instantiate HTTPSTransportService
            httpsTransport = new HTTPSTransportService();

            // Register the HTTPSTransportService under TransportService interface.
            // This will make TransportManagement component to find this.
            ctxt.getBundleContext().registerService(TransportService.class.getName(), httpsTransport, null);

            if (log.isDebugEnabled()) {
                log.debug("Successfully registered the https transport service");
            }

        } catch (Exception e) {
            log.error("Error while activating the HTTPS transport management bundle", e);
        }
    }

    protected void deactivate(ComponentContext ctxt) {
        log.debug("******* HTTPS Transport bundle is deactivated ******* ");
    }

    protected void setConfigurationContextService(ConfigurationContextService contextService) {
        this.contextService = contextService;
    }

    protected void unsetConfigurationContextService(ConfigurationContextService contextService) {
        this.contextService = null;
    }
}