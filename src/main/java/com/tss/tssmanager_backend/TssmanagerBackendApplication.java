package com.tss.tssmanager_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableScheduling
@EnableCaching
@EnableAsync
@EnableAspectJAutoProxy
@SpringBootApplication(exclude = {org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class TssmanagerBackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(TssmanagerBackendApplication.class, args);
	}
}