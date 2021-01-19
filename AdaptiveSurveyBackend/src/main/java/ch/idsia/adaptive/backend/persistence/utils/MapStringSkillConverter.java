package ch.idsia.adaptive.backend.persistence.utils;

import ch.idsia.adaptive.backend.persistence.model.Skill;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.AttributeConverter;
import java.util.Map;

/**
 * Author:  Claudio "Dna" Bonesana
 * Project: AdaptiveSurvey
 * Date:    26.11.2020 13:40
 */
public class MapStringSkillConverter implements AttributeConverter<Map<String, Skill>, String> {

	private static final ObjectMapper om = new ObjectMapper();

	@Override
	public String convertToDatabaseColumn(Map<String, Skill> meta) {
		try {
			return om.writeValueAsString(meta);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public Map<String, Skill> convertToEntityAttribute(String dbData) {
		try {
			TypeReference<Map<String, Skill>> t = new TypeReference<>() {
			};
			return om.readValue(dbData, t);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;
		}
	}
}
