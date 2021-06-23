package ch.idsia.adaptive.backend.services;

import ch.idsia.adaptive.backend.persistence.dao.SurveyRepository;
import ch.idsia.adaptive.backend.persistence.external.ImportStructure;
import ch.idsia.adaptive.backend.persistence.external.ModelStructure;
import ch.idsia.adaptive.backend.persistence.model.*;
import ch.idsia.crema.factor.bayesian.BayesianFactor;
import ch.idsia.crema.factor.bayesian.BayesianFactorFactory;
import ch.idsia.crema.model.graphical.BayesianNetwork;
import ch.idsia.crema.model.io.uai.BayesUAIWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ch.idsia.adaptive.backend.config.Consts.NO_SKILL;

/**
 * Author:  Claudio "Dna" Bonesana
 * Project: AdapQuest
 * Date:    12.01.2021 17:11
 */
@Service
public class InitializationService {
	private static final Logger logger = LogManager.getLogger(InitializationService.class);

	private final SurveyRepository surveys;

	private final ObjectMapper om = new ObjectMapper();

	@Autowired
	public InitializationService(SurveyRepository surveys) {
		this.surveys = surveys;
	}

	public void init() {
		long surveysNum = surveys.count();

		if (surveysNum > 0) {
			logger.info("Data already initialized: {} surveys found(s)", surveysNum);
			return;
		}

		readDataFolder();

		logger.info("Data initialization completed with {} survey(s)", surveys.count());
	}

	void readDataFolder() {
		Path cwd = Paths.get("");
		try (Stream<Path> paths = Files.walk(cwd.resolve("data"))) {
			paths
					.filter(Files::isRegularFile)
					.map(path -> {
						try {
							logger.info("Reading file={}", path.toFile());
							return om.readValue(path.toFile(), ImportStructure.class);
						} catch (IOException e) {
							logger.error("Could not import path={}", path);
							logger.error(e);
							return null;
						}
					})
					.filter(Objects::nonNull)
					.forEach(this::parseSurvey);
		} catch (IOException e) {
			logger.error(e);
		}
	}

	public Survey parseSurvey(ImportStructure structure) {
		final Survey survey = parseSurveyStructure(structure);
		// save survey
		return surveys.save(survey);
	}

	public static String parseModelStructure(ModelStructure model, Map<String, Integer> variables) {
		logger.info("Parsing model structure with {} variable(s).", model.variables.size());
		final BayesianNetwork bn = new BayesianNetwork();
		variables.putAll(model.variables.stream()
				.collect(Collectors.toMap(
						// collect map of variables
						s -> s.name,
						k -> bn.addVariable(k.states)
				))
		);

		model.variables.stream()
				.peek(
						// set parents
						s -> s.parents.forEach(
								p -> bn.addParent(variables.get(s.name), variables.get(p))
						)
				)
				.forEach(s -> {
					// get domain from variables
					int[] x = new int[s.parents.size() + 1];
					x[0] = variables.get(s.name);
					for (int i = 1; i < x.length; i++)
						x[i] = variables.get(s.parents.get(i - 1));

					// build factor
					final BayesianFactor bf = BayesianFactorFactory.factory().domain(bn.getDomain(x)).data(s.data).get();
					bn.setFactor(x[0], bf);
				});

		logger.info("Model structure parsing completed.");

		List<String> modelData = new BayesUAIWriter(bn, "").serialize();

		return String.join("\n", modelData);
	}

	public static Survey parseSurveyStructure(ImportStructure structure) {
		// build model
		final Map<String, Integer> v = new HashMap<>();
		String modelData = "";

		if (structure.modelData != null) {
			logger.info("Using serialized model structure.");
			modelData = structure.modelData;
			structure.skills.forEach(skill -> v.put(skill.name, skill.variable));
			structure.questions.forEach(question -> v.put(question.name, question.variable));
		} else if (structure.model != null) {
			modelData = parseModelStructure(structure.model, v);
		}

		// build skills
		final Map<String, Skill> skills = structure.skills.stream()
				.map(s -> new Skill()
						.setName(s.name)
						.setVariable(v.computeIfAbsent(s.name, i -> -1))
						.setStates(s.states.stream()
								.map(l -> new SkillState(l.name, l.value))
								.collect(Collectors.toList())
						)
				)
				.collect(Collectors.toMap(Skill::getName, x -> x));

		if (structure.survey.getSimple()) {
			skills.put("", new Skill().setName(NO_SKILL).setStates(new ArrayList<>()));
			logger.info("Simple survey: added empty skill {}", NO_SKILL);
		}

		logger.info("Found {} skill(s): {}", skills.size(), skills.values().stream().map(Skill::getName).collect(Collectors.joining(" ")));

		// build questions
		final Set<Question> questions = structure.questions.stream()
				.map(q -> new Question()
						.setQuestion(q.question)
						.setExplanation(q.explanation)
						.setSkill(skills.get(q.skill))
						.setName(q.name)
						.setVariable(v.computeIfAbsent(q.name, i -> -1))
						.setWeight(q.weight)
						.setIsExample(q.example)
						.setRandomAnswers(q.randomAnswers)
						.setMandatory(q.mandatory)
						.addAnswersAvailable(q.answers.stream()
								.map(a -> new QuestionAnswer()
										.setText(a.text)
										.setState(a.state)
										.setIsCorrect(a.correct)
								)
								.toArray(QuestionAnswer[]::new)
						)
				).collect(Collectors.toCollection(LinkedHashSet::new));
		logger.info("Found {} question(s)", questions.size());

		// build survey
		Survey survey = new Survey()
				.setLanguage(structure.survey.language)
				.setAccessCode(structure.survey.accessCode)
				.setDescription(structure.survey.description)
				.setDuration(structure.survey.duration)
				.setQuestions(questions)
				.setSkillOrder(structure.survey.skillOrder)
				.setSkills(new HashSet<>(skills.values()))
				.setModelData(modelData)
				.setMixedSkillOrder(structure.survey.mixedSkillOrder)
				.setIsAdaptive(structure.survey.adaptive)
				.setIsSimple(structure.survey.simple)
				.setQuestionsAreRandom(structure.survey.randomQuestions)
				.setQuestionPerSkillMin(structure.survey.questionPerSkillMin)
				.setQuestionPerSkillMax(structure.survey.questionPerSkillMax)
				.setScoreUpperThreshold(structure.survey.scoreUpperThreshold)
				.setScoreLowerThreshold(structure.survey.scoreLowerThreshold)
				.setGlobalMeanScoreUpperThreshold(structure.survey.globalMeanScoreUpperThreshold)
				.setGlobalMeanScoreLowerThreshold(structure.survey.globalMeanScoreLowerThreshold)
				.setQuestionTotalMin(structure.survey.questionTotalMin)
				.setQuestionTotalMax(structure.survey.questionTotalMax);

		questions.forEach(q -> q.setSurvey(survey));
		skills.values().forEach(s -> s.setSurvey(survey));

		logger.info("Found survey with accessCode={}", survey.getAccessCode());

		return survey;
	}

}