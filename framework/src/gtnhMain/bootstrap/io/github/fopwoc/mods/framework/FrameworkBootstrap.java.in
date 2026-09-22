package io.github.fopwoc.mods.framework;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = "@MOD_ID@",
    name = "@MOD_NAME@",
    version = "@MOD_VERSION@",
    dependencies = "required-after:forgelin;required-after:hodgepodge;",
    acceptableRemoteVersions = "*")
public final class FrameworkBootstrap {
  private static final Logger LOGGER = LogManager.getLogger(FrameworkBootstrap.class);

  public FrameworkBootstrap() {
    requireSupportedJava(System.getProperty("java.specification.version"));
  }

  public static void requireSupportedJava(String specificationVersion) {
    final int feature;
    try {
      String version = specificationVersion.startsWith("1.")
          ? specificationVersion.substring(2)
          : specificationVersion;
      feature = Integer.parseInt(version);
    } catch (RuntimeException exception) {
      String message = "KNH Core could not determine the Java version: " + specificationVersion
          + ". Java 24-26 is required.";
      LOGGER.error(message, exception);
      throw new IllegalStateException(message, exception);
    }

    if (feature < 24 || feature > 26) {
      String message = "KNH Core supports Java 24-26; found Java " + feature
          + ". Select a supported Java runtime for this GTNH instance.";
      LOGGER.error(message);
      throw new IllegalStateException(message);
    }
  }

  @Mod.EventHandler
  public void onInit(FMLInitializationEvent event) {
    invoke("io.github.fopwoc.mods.framework.FrameworkMod", "onInit");
  }

  @Mod.EventHandler
  public void onServerStarted(FMLServerStartedEvent event) {
    invoke("io.github.fopwoc.mods.framework.platform.GtnhServerEvents", "serverStarted");
  }

  @Mod.EventHandler
  public void onServerStopping(FMLServerStoppingEvent event) {
    invoke("io.github.fopwoc.mods.framework.platform.GtnhServerEvents", "serverStopping");
  }

  /** The Kotlin side is loaded reflectively so this class stays loadable on any Java version. */
  private static void invoke(String objectClass, String method) {
    try {
      Class<?> type = Class.forName(objectClass);
      Object instance = type.getField("INSTANCE").get(null);
      type.getMethod(method).invoke(instance);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("KNH Core failed to run " + objectClass + "." + method, exception);
    }
  }
}
