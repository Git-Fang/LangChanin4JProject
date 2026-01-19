package org.fb.config;

import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!standalone")
@EnableDiscoveryClient
public class DiscoveryClientConfig {
}
