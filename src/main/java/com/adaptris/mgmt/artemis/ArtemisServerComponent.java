/*
 * Copyright 2015 Adaptris Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.adaptris.mgmt.artemis;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.management.MalformedObjectNameException;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adaptris.core.management.ManagementComponent;

/**
 * Management component that starts up an embedded ActiveMQ broker.
 * 
 * @author amcgrath.
 *
 */
public class ArtemisServerComponent implements ManagementComponent {
  
  private transient Logger log = LoggerFactory.getLogger(this.getClass().getName());

  /**
   * The property key that defines the artemis configuration file.
   * 
   */
  public static final String ARTEMIS_BROKER_CONFIG_FILE_NAME_KEY = "activemq.config.filename";
  
  private static final String DEFAULT_ARTEMIS_CONFIG = "broker.xml"; // should be on the classpath
  
  private transient ClassLoader classLoader;
  
  private transient Properties properties;
  
  private transient volatile EmbeddedActiveMQ embeddedArtemis;
  
  @Override
  public void setClassLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  @Override
  public void init(final Properties properties) throws Exception {
    this.properties = properties;
    embeddedArtemis = new EmbeddedActiveMQ();
  }

  @Override
  public void start() throws Exception {
    if (classLoader == null) {
      classLoader = Thread.currentThread().getContextClassLoader();
    }
    
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          log.debug("Creating Apache Artemis Broker");
          Thread.currentThread().setContextClassLoader(classLoader);
          
          String brokerConfigFileName = null;
          InputStream brokerXmlStream = null;
          if(properties.getProperty(ARTEMIS_BROKER_CONFIG_FILE_NAME_KEY) != null) {
            brokerXmlStream = this.getClass().getClassLoader().getResourceAsStream(properties.getProperty(ARTEMIS_BROKER_CONFIG_FILE_NAME_KEY));
            if(brokerXmlStream != null) {// we have a broker.xml file to apply to the broker.
              brokerConfigFileName = properties.getProperty(ARTEMIS_BROKER_CONFIG_FILE_NAME_KEY);
              log.info("Found Artemis configuration file {}.  Starting broker.", brokerConfigFileName);
            }
            else
              log.warn("Broker config file {} not found on the classpath, starting the broker with minimal configuration.", properties.getProperty(ARTEMIS_BROKER_CONFIG_FILE_NAME_KEY));
          } else {
            brokerXmlStream = this.getClass().getClassLoader().getResourceAsStream(DEFAULT_ARTEMIS_CONFIG);
            if(brokerXmlStream != null) { // we have a broker.xml file to apply to the broker.
              brokerConfigFileName = DEFAULT_ARTEMIS_CONFIG;
              log.info("Found Artemis configuration file {}.  Starting broker.", brokerConfigFileName);
            }
            else
              log.warn("Broker config file {} not found on the classpath, starting the broker with minimal configuration.", DEFAULT_ARTEMIS_CONFIG);
          }
          
          if(brokerConfigFileName != null) {
            createConfiguredBroker(embeddedArtemis, brokerConfigFileName);
          } else { // minimal configuration
            log.info("Creating minimal Artemis broker, without security.");
            createMinimalBroker(embeddedArtemis);
          }
          
          embeddedArtemis.start();
          embeddedArtemis.waitClusterForming(1l, TimeUnit.MINUTES, 3, 1);
          
          log.debug("Apache Artemis broker now running.");
        } catch (Throwable ex) {
          log.error("Could not start the Apache Artemis broker", ex);
        }
      }
    }).start();
    
  }
  
  /**
   * 
   * @return
   * @throws Exception
   */
  private void createMinimalBroker(EmbeddedActiveMQ broker) throws Exception {
    Configuration config = new ConfigurationImpl();

    config.setSecurityEnabled(false);
    config.addAcceptorConfiguration("in-vm", "vm://0");
    config.addAcceptorConfiguration("tcp", "tcp://127.0.0.1:61616");

    broker.setConfiguration(config);
  }
  
  private void createConfiguredBroker(EmbeddedActiveMQ broker, String configFileName) throws Exception {
    broker.setConfigResourcePath(configFileName);
  }

  @Override
  public void stop() throws Exception {
    
      if (embeddedArtemis != null) {
        try {
          embeddedArtemis.stop();
        } catch (Throwable t) {
          // silently
        }
      }
    log.debug(this.getClass().getSimpleName() + " Stopped");
  }

  @Override
  public void destroy() throws Exception {
    log.debug(this.getClass().getSimpleName() + " Destroyed");
  }

  void waitForStart(long timeout) throws InterruptedException, TimeoutException {
    long totalWaitTime = 0;
    while (!brokerStarted() && totalWaitTime < timeout) {
      Thread.sleep(100);
      totalWaitTime += 100;
    }
    if (totalWaitTime > timeout) {
      throw new TimeoutException("Timeout Expired");
    }
  }

  String brokerName() throws MalformedObjectNameException {
    return embeddedArtemis.getActiveMQServer().describe();
  }

  private boolean brokerStarted() {
    if(embeddedArtemis == null)
      return false;
    else {
      if(embeddedArtemis.getActiveMQServer() == null)
        return false;
      else
        return embeddedArtemis.getActiveMQServer().isActive();
    }
  }
}
