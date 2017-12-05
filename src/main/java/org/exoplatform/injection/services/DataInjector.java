package org.exoplatform.injection.services;

import java.util.Map;

public interface DataInjector {

    void inject(Map<String, Integer> completion) throws Exception;

    void purge(Map<String, Integer> completion) throws Exception;

    Map setup (String pathToDatafolder) throws RuntimeException;
}
