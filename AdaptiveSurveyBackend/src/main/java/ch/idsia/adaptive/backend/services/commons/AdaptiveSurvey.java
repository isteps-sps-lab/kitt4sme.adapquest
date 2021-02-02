package ch.idsia.adaptive.backend.services.commons;

import ch.idsia.adaptive.backend.persistence.model.Question;
import ch.idsia.adaptive.backend.persistence.model.Skill;
import ch.idsia.adaptive.backend.persistence.model.SkillLevel;
import ch.idsia.adaptive.backend.persistence.model.Survey;
import ch.idsia.crema.entropy.BayesianEntropy;
import ch.idsia.crema.factor.bayesian.BayesianFactor;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Author:  Claudio "Dna" Bonesana
 * Project: AdaptiveSurvey
 * Date:    14.12.2020 17:17
 */
public class AdaptiveSurvey extends NonAdaptiveSurvey {
	private static final Logger logger = LogManager.getLogger(AdaptiveSurvey.class);

	protected Map<Skill, Integer> questionsDonePerSkill = new HashMap<>();
	protected Map<Skill, LinkedList<Question>> availableQuestionsPerSkill = new HashMap<>();


	public AdaptiveSurvey(Survey model, Long seed) {
		super(model, seed);
	}

	@Override
	public void addQuestions(List<Question> questions) {
		questions.forEach(q -> {
			Skill skill = q.getSkill();

			// for AbstractSurvey class
			this.questions.add(q);
			this.skills.add(skill);

			// for this class
			this.questionsDonePerSkill.putIfAbsent(skill, 0);
			this.availableQuestionsPerSkill.computeIfAbsent(skill, x -> new LinkedList<>()).add(q);
		});
	}

	/**
	 * Check if the given {@link Skill} is valid in the current state or not. The condition for a {@link Skill} to be
	 * valid are:
	 * <li>the number of questions are below the minimum;</li>
	 * <li>the number of questions are below are above the maximum;</li>
	 * <li>there still are questions available.</li>
	 *
	 * @param skill the skill to test
	 * @return true if the skill is valid, otherwise false.
	 */
	public boolean isSkillValid(Skill skill) {
		Integer questionsDone = questionsDonePerSkill.get(skill);

		if (availableQuestionsPerSkill.get(skill).isEmpty()) {
			// the skill has no questions available
			logger.debug("skill={} has no questions available", skill.getName());
			return false;
		}

		if (questionsDone <= survey.getQuestionPerSkillMin()) {
			// we need to make more questions for this skill
			return true;
		}

		// we can make more questions if we are below the maximum amount
		final boolean b = questionsDone <= survey.getQuestionPerSkillMax();

		if (!b)
			logger.debug("skill={} reached max questions per skill (done= {}, max={})", skill.getName(), questionsDone, survey.getQuestionPerSkillMax());

		return b;
	}

	/**
	 * Set a {@link Skill} to be invalid by reducing the number of {@link SkillLevel} to zero.
	 *
	 * @param skill the skill to invalidate
	 */
	public void invalidateSkill(Skill skill) {
		availableQuestionsPerSkill.put(skill, new LinkedList<>());
	}

	@Override
	public boolean isFinished() {
		if (questions.isEmpty()) {
			// we don't have any more question
			logger.debug("survey finished with no more questions");
			return true;
		}

		if (questionsDone.size() > survey.getQuestionTotalMax()) {
			// we made too many questions
			logger.debug("survey finished with too many questions (done={}, max={})", questionsDone.size(), survey.getQuestionTotalMax());
			return true;
		}

		if (questionsDone.size() < survey.getQuestionTotalMin() && skills.stream().anyMatch(this::isSkillValid)) {
			// we need to make more questions and there still are skills that are valid
			return false;
		}

		// all skills are depleted?
		final boolean b = availableQuestionsPerSkill.values().stream().allMatch(Collection::isEmpty);

		if (b)
			logger.debug("survey finished with no more valid skills");

		return b;
	}

	@Override
	public Question next() throws SurveyException {
		if (!answered && currentQuestion != null)
			return currentQuestion;

		Map<H, List<Double>> map = new HashMap<>();

		for (Skill skill : skills) {
			Integer S = skill.getVariable();

			if (!isSkillValid(skill)) {
				logger.debug("skill={} is not valid", skill.getName());
				continue;
			}

			for (Question question : availableQuestionsPerSkill.get(skill)) {
				final Integer Q = question.getVariable();
				final int size = network.getSize(Q);

				double h = 0;

				for (int i = 0; i < size; i++) {
					TIntIntMap obs = new TIntIntHashMap(observations);
					obs.put(Q, i);
					BayesianFactor pS = inference.query(S, obs);
					double v = BayesianEntropy.H(pS);
					v = Double.isNaN(v) ? 0.0 : v;
					h += v;
				}

				h /= size;

				H x = new H(skill, question);
				map.computeIfAbsent(x, i -> new ArrayList<>()).add(h);
			}
		}

		// compute skill average entropy
		Question nextQuestion = null;
		Skill nextSkill = null;
		double minH = Double.MAX_VALUE;

		for (Map.Entry<H, List<Double>> entry : map.entrySet()) {
			H h = entry.getKey();
			List<Double> vs = entry.getValue();
			final double v = vs.stream().reduce(Double::sum).orElse(0.0) / vs.size();

			logger.debug("skill={} question={} with informationGain={}", h.skill.getName(), h.question.getName(), v);

			if (v < minH) {
				nextQuestion = h.question;
				nextSkill = h.skill;
				minH = v;
			}
		}

		if (nextQuestion == null)
			// this is also valid for nextSkill == null
			throw new SurveyException("No valid question found!");

		// register the chosen question as nextQuestion and maps
		logger.debug("next question is skill={} level={} with entropy={}", nextSkill.getName(), nextQuestion.getName(), minH);
		register(nextQuestion);

		if (questionsDonePerSkill.get(nextSkill) > survey.getQuestionPerSkillMin() && questionsDone.size() > survey.getQuestionTotalMin()) {
			if (minH > survey.getEntropyUpperThreshold() || minH < survey.getEntropyLowerThreshold()) {
				// skill entropy level achieved
				invalidateSkill(nextSkill);
			}

			if (questionsDonePerSkill.get(nextSkill) > survey.getQuestionPerSkillMax()) {
				// too many questions per skill
				invalidateSkill(nextSkill);
			}
		}

		return currentQuestion;
	}

	private void register(Question q) {
		Skill s = q.getSkill();

		// remove from possible questions
		availableQuestionsPerSkill.get(s).remove(q);
		questions.remove(q);

		// add to done slacks
		questionsDonePerSkill.put(s, questionsDonePerSkill.get(s) + 1);
		questionsDone.add(q);

		// update current question
		currentQuestion = q;
	}
}
