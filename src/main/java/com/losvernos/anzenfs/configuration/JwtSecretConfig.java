package com.losvernos.anzenfs.configuration;

import com.losvernos.anzenfs.files.FileUtils;
import com.losvernos.anzenfs.security.JwtSecretStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtSecretConfig {

  @Bean
  public String jwtSigningSecret() {
    return JwtSecretStore.loadOrGenerate(FileUtils.getDataDir());
  }
}
