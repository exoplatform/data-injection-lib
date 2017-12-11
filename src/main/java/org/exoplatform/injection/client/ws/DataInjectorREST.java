package org.exoplatform.injection.client.ws;

import org.exoplatform.injection.services.DataInjector;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.*;
import java.util.HashMap;

@Path("data/injection/")
public class DataInjectorREST implements ResourceContainer {
    private static final Log log = ExoLogger.getLogger(DataInjectorREST.class);

    private DataInjector dataInjector;

    private static final CacheControl cacheControl_ = new CacheControl();

    static {
        cacheControl_.setNoCache(true);
        cacheControl_.setNoStore(true);
    }

    public DataInjectorREST(DataInjector dataInjector) {
        this.dataInjector = dataInjector;

    }

    @GET
    @Path("inject")
    @RolesAllowed("administrators")
    public Response inject(@Context SecurityContext sc, @Context UriInfo uriInfo) throws Exception {

        try {
            this.dataInjector.inject(new HashMap<>());

        } catch (Exception e) {
            log.error("Data Injection Failed", e);
            return Response.serverError()
                    .entity(String.format("Data injection failed due to %1$s", e.getMessage()))
                    .build();
        }
        return Response.ok(String.format("Data has been injected successfully!!!"),
                MediaType.TEXT_PLAIN)
                .cacheControl(cacheControl_)
                .build();
    }

    @GET
    @Path("purge")
    @RolesAllowed("administrators")
    public Response purge(@Context SecurityContext sc, @Context UriInfo uriInfo) throws Exception {
        try {
            this.dataInjector.purge(new HashMap<>());
        } catch (Exception e) {
            log.error("Data purging failed", e);
            return Response.serverError()
                    .entity(String.format("Purge data failed due to %1$s", e.getMessage()))
                    .build();
        }
        return Response.ok(String.format("Data has been purged successfully!!!"),
                MediaType.TEXT_PLAIN)
                .cacheControl(cacheControl_)
                .build();
    }
}
