package ch.idsia.adaptive.experiments.kitt4sme;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Author:  Claudio "Dna" Bonesana
 * Project: adapquest
 * Date:    29.09.2021 16:08
 */
public class ReadXLSX {

	@Test
	void readingXLSXFile() throws Exception {

		final File file = new File("AdaptiveQuestionnaire.xlsx");

		final Workbook workbook = new XSSFWorkbook(file);

		final Sheet sheetSections = workbook.getSheet("Sections");
		final Sheet sheetAllQuestions = workbook.getSheet("All Questions");
		final Sheet sheetAnswers = workbook.getSheet("Answers");
		final Sheet sheetBinaryQuestions = workbook.getSheet("Binary questions");

		final List<KSection> sections = new ArrayList<>();
		final List<KQuestion> questions = new ArrayList<>();
		final List<KAnswer> answers = new ArrayList<>();
		final List<String> variables = new ArrayList<>();
		final List<KBinaryQuestion> binaryQuestions = new ArrayList<>();

		int i;

		i = 0;
		for (Row row : sheetSections) {
			if (i++ == 0)
				continue;

			if (row.getCell(0) == null || row.getCell(0).toString().isEmpty())
				continue;

			final int id = Double.valueOf(row.getCell(0).getNumericCellValue()).intValue();
			final String section = row.getCell(1).getStringCellValue();
			sections.add(new KSection(id, section));
		}

		i = 0;
		for (Row row : sheetAllQuestions) {
			if (i++ == 0)
				continue;

			if (row.getCell(0) == null || row.getCell(0).toString().isEmpty())
				continue;

			final int qid = Double.valueOf(row.getCell(0).getNumericCellValue()).intValue();
			final int sid = Double.valueOf(row.getCell(1).getNumericCellValue()).intValue();
			final boolean man = Double.valueOf(row.getCell(2).getNumericCellValue()).intValue() == 1;
			final boolean exc = Double.valueOf(row.getCell(3).getNumericCellValue()).intValue() == 1;
			final String text = row.getCell(4).getStringCellValue();

			questions.add(new KQuestion(qid, sid, man, exc, text));
		}

		i = 0;
		int limit = 0;
		for (Row row : sheetAnswers) {
			if (i++ == 0) {
				limit = row.getLastCellNum();
				continue;
			}

			if (row.getCell(0) == null || row.getCell(0).toString().isEmpty())
				continue;

			final int qid = Double.valueOf(row.getCell(0).getNumericCellValue()).intValue();

			for (int j = 2; j < limit; j += 2) {
				Cell cid = row.getCell(j);
				Cell txt = row.getCell(j + 1);
				if (row.getCell(j) == null || row.getCell(j + 1) == null || cid.toString().isEmpty() || txt.toString().isEmpty())
					continue;

				final int aid = Double.valueOf(row.getCell(j).getNumericCellValue()).intValue();
				final String text = row.getCell(j + 1).getStringCellValue();

				answers.add(new KAnswer(qid, aid, text));
			}
		}

		final Map<Integer, Map<Integer, Cell>> regions = new HashMap<>();

		for (CellRangeAddress region : sheetBinaryQuestions.getMergedRegions()) {
			final int firstColumn = region.getFirstColumn();
			final int firstRow = region.getFirstRow();
			final int lastRow = region.getLastRow();
			final Cell cell = sheetBinaryQuestions.getRow(firstRow).getCell(firstColumn);

			for (int row = firstRow; row <= lastRow; row++) {
				regions.computeIfAbsent(row, x -> new HashMap<>()).put(firstColumn, cell);
			}
		}

		i = 0;
		int bqid = 0;
		limit = 0;
		for (Row row : sheetBinaryQuestions) {
			if (i == 0) {
				i++;
				continue;
			}
			if (i == 1) {
				for (int j = 5; j < row.getLastCellNum(); j++) {
					if (row.getCell(j) != null && !row.getCell(j).toString().isEmpty())
						variables.add(row.getCell(j).getStringCellValue());
				}
				limit = variables.size();
				i++;
				continue;
			}

			if (row.getCell(5) == null || row.getCell(5).toString().isEmpty())
				// skip empty rows or not model-related questions
				continue;

			if (!regions.containsKey(i))
				continue;

			final int qid = Double.valueOf(regions.get(i).get(0).getNumericCellValue()).intValue();
			final boolean man = Double.valueOf(regions.get(i).get(1).getNumericCellValue()).intValue() == 1;
			final String qText = regions.get(i).get(2).getStringCellValue();
			final int aid = Double.valueOf(row.getCell(3).getNumericCellValue()).intValue();
			final String bqText = row.getCell(4).getStringCellValue();

			bqid++;
			i++;

			final KBinaryQuestion bq = new KBinaryQuestion(qid, aid, bqid, man, qText, bqText);
			binaryQuestions.add(bq);

			for (int j = 5, k = 0; k < limit; j++, k++) {
				final Cell c = row.getCell(j);
				final double v = c.toString().equals("KO") ? -1.0 : c.getNumericCellValue();
				bq.values.put(variables.get(k), v);
			}
		}

		final Map<String, KAnswer> ansMap = answers.stream()
				.collect(Collectors.toMap(k -> k.questionId + "$" + k.answerId, a -> a, (e1, e2) -> e1, LinkedHashMap::new));
		final Map<Integer, List<KBinaryQuestion>> bqMap = binaryQuestions.stream()
				.collect(Collectors.groupingBy(k -> k.questionId, LinkedHashMap::new, Collectors.toList()));

		final ObjectMapper om = new ObjectMapper();

		final KModelMultiple modelMultiple = new KModelMultiple();
		variables.forEach(modelMultiple::addSkill);
		modelMultiple.addQuestion(questions, ansMap, bqMap);

		try (BufferedWriter bw = new BufferedWriter(new FileWriter("AdaptiveQuestionnaire.multiple.model.txt"))) {
			bw.write(modelMultiple.model());
		}
		try (BufferedWriter bw = new BufferedWriter(new FileWriter("AdaptiveQuestionnaire.multiple.survey.json"))) {
			bw.write(om.writerWithDefaultPrettyPrinter().writeValueAsString(modelMultiple.structure()));
		}

		final KModelSingle modelSingle = new KModelSingle();
		variables.forEach(modelSingle::addSkill);
		bqMap.forEach((k, v) -> modelSingle.addQuestion(v));

		try (BufferedWriter bw = new BufferedWriter(new FileWriter("AdaptiveQuestionnaire.single.model.txt"))) {
			bw.write(modelSingle.model());
		}
		try (BufferedWriter bw = new BufferedWriter(new FileWriter("AdaptiveQuestionnaire.single.survey.json"))) {
			bw.write(om.writerWithDefaultPrettyPrinter().writeValueAsString(modelSingle.structure()));
		}

//		sections.forEach(System.out::println);
//		questions.forEach(System.out::println);
//		answers.forEach(System.out::println);
//		variables.forEach(System.out::println);
//		binaryQuestions.forEach(System.out::println);
	}
}
