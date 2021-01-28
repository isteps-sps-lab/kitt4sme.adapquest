package ch.idsia.adaptive.backend.config;

import ch.idsia.adaptive.backend.persistence.dao.ClientRepository;
import ch.idsia.adaptive.backend.persistence.model.Client;
import ch.idsia.adaptive.backend.security.APIKeyAuthFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * Author:  Claudio "Dna" Bonesana
 * Project: AdaptiveSurvey
 * Date:    28.01.2021 17:25
 */
@Configuration
@EnableWebSecurity
@AutoConfigureAfter(PersistenceConfig.class)
public class SecurityConfig extends WebSecurityConfigurerAdapter {
	public static final Logger logger = LogManager.getLogger(WebSecurityConfigurerAdapter.class);

	private final ClientRepository clients;

	@Value("${magic.api.key}")
	private String magicApiKey;

	@Autowired
	public SecurityConfig(ClientRepository clients) {
		this.clients = clients;
	}

	@Override
	protected void configure(HttpSecurity httpSecurity) throws Exception {
		APIKeyAuthFilter filter = new APIKeyAuthFilter("APIKey");
		filter.setAuthenticationManager(authentication -> {
			final String apiKey = (String) authentication.getPrincipal();
			if (magicApiKey.equals(apiKey)) {
				authentication.setAuthenticated(true);
				return authentication;
			}

			final Client client = clients.findByKey(apiKey);
			if (client == null) {
				logger.warn("API key={} not found or invalid", apiKey);
				throw new BadCredentialsException("API Key not found or not valid");
			}
			authentication.setAuthenticated(true);
			return authentication;
		});

		httpSecurity.antMatcher("/console/**")
				.csrf()
				.disable()
				.sessionManagement()
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
				.and()
				.addFilter(filter)
				.authorizeRequests()
				.anyRequest()
				.authenticated();
	}
}
