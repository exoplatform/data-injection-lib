package org.exoplatform.injection.client.scheduler;

import org.exoplatform.injection.services.DataInjector;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.HashMap;

public class DataInjectorScheduler implements Job {

    private static final Log LOG = ExoLogger.getLogger(DataInjectorScheduler.class);

    private DataInjector dataInjector;

    public DataInjectorScheduler(DataInjector dataInjector) {
        this.dataInjector = dataInjector;
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        try {
            this.dataInjector.inject(new HashMap<>());
        } catch (Exception e) {
            LOG.error("Data Injection Failed", e);
        }

    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
