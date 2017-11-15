package org.exoplatform.injection.services;

public interface DataInjector {

    void inject() throws Exception;

    void purge() throws Exception;
}
