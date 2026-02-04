package com.jibra.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /*
       Biggest Problem — Load Balancers / Proxies

       In production, traffic usually comes through:
        Nginx
        AWS ALB
        Cloudflare
        Kubernetes Ingress

When that happens:
        getRemoteAddress() becomes the load balancer IP, not the user.
     */

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest()
                        .getRemoteAddress()
                        .getAddress()
                        .getHostAddress();
    }

    /*

    Production-Safe Version (Use X-Forwarded-For)

     Now it is production-ready
     */

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {

            String forwarded = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Forwarded-For");

            if (forwarded != null && !forwarded.isEmpty()) {
                return Mono.just(forwarded.split(",")[0]); // real client IP
            }

            var remoteAddress = exchange.getRequest().getRemoteAddress();

            return Mono.just(
                    remoteAddress != null
                            ? remoteAddress.getAddress().getHostAddress()
                            : "unknown"
            );
        };
    }

    /*
          But Honestly — IP Rate Limiting is Weak
          Modern systems prefer:
               Rate limit per USER, not per IP.
          Why?
           Mobile carriers share IPs
           Offices share IPs
           NAT hides users

           Rate limit by JWT subject

           Use different limits for anonymous vs authenticated users.

           Example:
             Anonymous → 5 req/sec
             Logged user → 50 req/sec
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange ->
                exchange.getPrincipal()
                        .map(principal -> principal.getName())
                        .defaultIfEmpty("anonymous");
    }


}
