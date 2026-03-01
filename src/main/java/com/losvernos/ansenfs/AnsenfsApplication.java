package com.losvernos.ansenfs;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@ImportRuntimeHints(AnsenfsApplication.WebResourcesHints.class)
@SpringBootApplication
public class AnsenfsApplication {

	static class WebResourcesHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // Force l'inclusion de tout le dossier static dans le binaire natif
            hints.resources().registerPattern("static/browser/**");
        }
    }

	public static void main(String[] args) {
		SpringApplication.run(AnsenfsApplication.class, args);
	}

}
