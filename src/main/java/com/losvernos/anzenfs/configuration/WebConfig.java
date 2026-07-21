package com.losvernos.anzenfs.configuration;

import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");

        registry.addViewController("/{path:[^\\.]*}")
                .setViewName("forward:/index.html");

        registry.addViewController("/**/{path:[^\\.]*}")
                .setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // index.html is the SPA shell: it references content-hashed bundle filenames that
        // change on every build, so it must never be cached - otherwise a browser holding an
        // old cached copy keeps loading a pre-fix JS bundle after a redeploy, invisible until
        // a hard refresh. The hashed JS/CSS bundles it points to are safe to cache long-term
        // since a rebuild always gives them a new filename.
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/browser/")
                .setCacheControl(CacheControl.noStore());

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/browser/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic());
    }
}
