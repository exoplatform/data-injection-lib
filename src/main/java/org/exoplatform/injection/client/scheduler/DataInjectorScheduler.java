package org.exoplatform.injection.client.scheduler;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.injection.helper.InjectorMonitor;
import org.exoplatform.injection.services.DataInjector;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.HashMap;

public class DataInjectorScheduler implements Job {

    private static final Log LOG = ExoLogger.getLogger(DataInjectorScheduler.class);
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {

            InjectorMonitor cwiMonitor = new InjectorMonitor("Inject Data");
            //--- Call purge service
            CommonsUtils.getService(DataInjector.class).inject(new HashMap<>());
            cwiMonitor.stop();
            LOG.info("Data Injection Job has been done successfully.............");
            LOG.info(cwiMonitor.prettyPrint());

        } catch (Exception e) {
            LOG.error("Failed to running Data Injection Job", e);
        }
    }
}
