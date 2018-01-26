package org.exoplatform.injection.services;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

abstract public class AbstractModule {

    protected static final String USER_MODULE_RANDOM_PASSWORD_PROPERTY= "exo.data.injection.password.random.enable";
    protected static final String SPACE_MODULE_PREFIX_PATTERN_VALUE= "exo.data.injection.space.prefix.value";
    protected static final String USER_MODULE_ENABLE= "true";


    public AbstractModule() {
    }

    public Log getLog() {
        return ExoLogger.getExoLogger(this.getClass());
    }
}
