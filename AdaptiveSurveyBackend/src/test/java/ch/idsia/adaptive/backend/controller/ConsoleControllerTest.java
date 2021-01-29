package ch.idsia.adaptive.backend.controller;

import ch.idsia.adaptive.backend.AdaptiveSurveyBackend;
import ch.idsia.adaptive.backend.config.PersistenceConfig;
import ch.idsia.adaptive.backend.config.WebConfig;
import ch.idsia.adaptive.backend.persistence.dao.ClientRepository;
import ch.idsia.adaptive.backend.persistence.dao.SurveyRepository;
import ch.idsia.adaptive.backend.persistence.external.ImportStructure;
import ch.idsia.adaptive.backend.persistence.external.SurveyStructure;
import ch.idsia.adaptive.backend.services.InitializationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import javax.transaction.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Author:  Claudio "Dna" Bonesana
 * Project: AdaptiveSurvey
 * Date:    29.01.2021 19:46
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = AdaptiveSurveyBackend.class)
@WebMvcTest({
		WebConfig.class,
		PersistenceConfig.class,
		SurveyRepository.class,
		ClientRepository.class,
		InitializationService.class,
		ConsoleController.class,
})
@Transactional
class ConsoleControllerTest {

	@Autowired
	ObjectMapper om;

	@Autowired
	MockMvc mvc;

	@Test
	public void testPostNewSurveyStructure() throws Exception {
		ImportStructure is = new ImportStructure()
				.setSurvey(new SurveyStructure());

		mvc
				.perform(post("/console/survey")
						.header("APIKey", "test")
						.contentType(MediaType.APPLICATION_JSON_VALUE)
						.content(om.writeValueAsString(is))
				).andExpect(status().isCreated());
	}
}