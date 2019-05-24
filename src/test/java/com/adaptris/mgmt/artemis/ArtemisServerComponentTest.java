package com.adaptris.mgmt.artemis;

import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;

import org.apache.commons.lang3.BooleanUtils;
import org.junit.Test;

import com.adaptris.core.BaseCase;
import com.adaptris.mgmt.artemis.ArtemisServerComponent;

public class ArtemisServerComponentTest {

  private static final Properties TEST_PROPERTIES;
  private static final String PROPERTIES_RESOURCE = "unit-tests.properties";
  private static final String TEST_ACTIVEMQ_CONFIG = "activemq.test.configuration";

  static {
    TEST_PROPERTIES = new Properties();

    InputStream in = BaseCase.class.getClassLoader().getResourceAsStream(PROPERTIES_RESOURCE);

    if (in == null) {
      throw new RuntimeException("cannot locate resource [" + PROPERTIES_RESOURCE + "] on classpath");
    }

    try {
      TEST_PROPERTIES.load(in);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static boolean skipTests() {
    return BooleanUtils.toBoolean(TEST_PROPERTIES.getProperty("activemq.skip.tests", "false"));
  }

  @Test
  public void testDefaultStart() throws Exception {
    if (!skipTests()) {
      ArtemisServerComponent comp = new ArtemisServerComponent();
      comp.init(new Properties());
      comp.setClassLoader(Thread.currentThread().getContextClassLoader());
      try {
        comp.start();
        Thread.sleep(1000);
        comp.waitForStart(60000);
      }
      catch (InterruptedException | TimeoutException e) {
        System.err.println("Failed to start");
      }
      finally {
        comp.stop();
      }
    }
  }

  @Test
  public void testStart_ConfiguredArtemis_XML() throws Exception {
    if (!skipTests()) {
      ArtemisServerComponent comp = new ArtemisServerComponent();
      comp.init(createBootProperties());
      try {
        comp.start();
        Thread.sleep(1000);
        comp.waitForStart(60000);
      }
      catch (InterruptedException | TimeoutException e) {
        System.err.println("Failed to start");
      }
      finally {
        comp.stop();
      }
    }
  }

  private Properties createBootProperties() {
    Properties result = new Properties();
    result.setProperty(ArtemisServerComponent.ARTEMIS_BROKER_CONFIG_FILE_NAME_KEY,
        relativize(TEST_PROPERTIES.getProperty(TEST_ACTIVEMQ_CONFIG)));
    return result;
  }

  private static String relativize(String s) {
    if (!isEmpty(s)) {
      return s.replaceAll(Matcher.quoteReplacement(System.getProperty("user.dir")), ".").replaceAll("\\\\", "/");
    }
    return s;
  }

}
