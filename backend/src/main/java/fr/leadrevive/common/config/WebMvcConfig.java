package fr.leadrevive.common.config;

import fr.leadrevive.common.tenant.TenantFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantFilter tenantFilter;

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(tenantFilter);
    }
}
