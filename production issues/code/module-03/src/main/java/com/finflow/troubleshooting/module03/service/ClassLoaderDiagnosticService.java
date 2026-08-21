package com.finflow.troubleshooting.module03.service;

import org.springframework.stereotype.Service;

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ClassLoaderDiagnosticService {

    public Map<String, Object> inspectClassOrigin(String fqcn) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queriedClassName", fqcn);

        try {
            Class<?> clazz = Class.forName(fqcn);
            result.put("classFound", true);
            result.put("canonicalName", clazz.getCanonicalName());

            ClassLoader classLoader = clazz.getClassLoader();
            result.put("classLoaderType", classLoader != null ? classLoader.getClass().getName() : "Bootstrap ClassLoader");

            ProtectionDomain protectionDomain = clazz.getProtectionDomain();
            if (protectionDomain != null) {
                CodeSource codeSource = protectionDomain.getCodeSource();
                if (codeSource != null && codeSource.getLocation() != null) {
                    URL location = codeSource.getLocation();
                    result.put("loadedJarLocation", location.toString());
                } else {
                    result.put("loadedJarLocation", "Unknown (No CodeSource)");
                }
            } else {
                result.put("loadedJarLocation", "Unknown (No ProtectionDomain)");
            }

            Package pkg = clazz.getPackage();
            if (pkg != null) {
                result.put("implementationTitle", pkg.getImplementationTitle());
                result.put("implementationVersion", pkg.getImplementationVersion());
                result.put("specificationVersion", pkg.getSpecificationVersion());
            }

        } catch (ClassNotFoundException e) {
            result.put("classFound", false);
            result.put("error", "ClassNotFoundException: " + e.getMessage());
        }

        return result;
    }
}
