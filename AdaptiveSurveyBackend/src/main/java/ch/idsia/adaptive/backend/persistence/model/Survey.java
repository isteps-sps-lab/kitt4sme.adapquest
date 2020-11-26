package ch.idsia.adaptive.backend.persistence.model;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.Set;

/**
 * Author:  Claudio "Dna" Bonesana
 * Project: AdaptiveSurvey
 * Date:    25.11.2020 12:58
 */
@Entity
@Data
@Accessors(chain = true)
public class Survey {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * Textual description of a survey.
	 */
	private String description;

	/**
	 * Access code for this kind of survey.
	 */
	private String accessCode;

	/**
	 * Duration in seconds of a survey.
	 */
	private Long duration;

	/**
	 * Set of questions available for this Survey.
	 */
	// TODO: maybe consider
	//  (1) a pool of questions shared between surveys?
	//  (2) group of questions instead of a single question?
	@OneToMany
	private Set<Question> questions;

	/**
	 * Survey Sessions open for this Survey.
	 */
	@OneToMany
	private Set<Session> sessions;
}
