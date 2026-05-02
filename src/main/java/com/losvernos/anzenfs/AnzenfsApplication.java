package com.losvernos.anzenfs;

import com.losvernos.anzenfs.database.DBInitializer;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.event.EventListener;

import jakarta.servlet.MultipartConfigElement;

@ImportRuntimeHints(AnzenfsApplication.WebResourcesHints.class)
@SpringBootApplication
public class AnzenfsApplication {
  @Autowired
  MultipartConfigElement multipartConfigElement;

  static class WebResourcesHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      // Force l'inclusion de tout le dossier static dans le binaire natif
      hints.resources().registerPattern("static/browser/**");
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  public void checkLimits() {
    System.out.println("Max File Size: " + multipartConfigElement.getMaxFileSize());
    System.out.println("Max Request Size: " + multipartConfigElement.getMaxRequestSize());
    DBInitializer.initialize();
  }

  static void main(String[] args) {
    SpringApplication.run(AnzenfsApplication.class, args);
  }

}
