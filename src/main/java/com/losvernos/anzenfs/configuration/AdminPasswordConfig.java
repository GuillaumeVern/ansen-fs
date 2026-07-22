package com.losvernos.anzenfs.configuration;

import com.losvernos.anzenfs.files.FileUtils;
import com.losvernos.anzenfs.security.AdminPasswordStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminPasswordConfig {

  @Bean
  public String initialAdminPassword() {
    return AdminPasswordStore.loadOrGenerate(FileUtils.getDataDir());
  }
}
