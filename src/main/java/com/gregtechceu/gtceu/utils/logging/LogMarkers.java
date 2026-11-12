package com.gregtechceu.gtceu.utils.logging;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.composite.CompositeConfiguration;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

public final class LogMarkers {

    /// Ore vein placement debug logging<br>
    /// <small>disabled by default</small>
    public static final Marker VEINDEBUG = MarkerManager.getMarker("VEINDEBUG");

    // region internals
    private LogMarkers() {}

    private static final String LOG_MARKER_PROPERTY_PREFIX = "gtceu.logging.marker.";
    private static final String LOG4J2_CONFIG_FILE_NAME = "gt-log4j2.xml";

    @ApiStatus.Internal
    public static void initializeLogFilters() {
        LoggerContext context = LoggerContext.getContext();
        AbstractConfiguration existingConfig = (AbstractConfiguration) context.getConfiguration();

        ConfigurationSource configSource = ConfigurationSource.fromResource(LOG4J2_CONFIG_FILE_NAME,
                LogMarkers.class.getClassLoader());
        AbstractConfiguration addedConfig = (AbstractConfiguration) ConfigurationFactory.getInstance()
                .getConfiguration(context, configSource);

        Configurator.reconfigure(new CompositeConfiguration(List.of(existingConfig, addedConfig)));
    }
    // endregion
}
