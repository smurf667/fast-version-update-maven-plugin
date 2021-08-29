package com.example.library;

import java.io.IOException;
import java.io.OutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.RAMDirectory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

public class Generator {

	public static void generatePDF(final String text, final OutputStream out) throws IOException {
		try (SpellChecker spellchecker = new SpellChecker(new RAMDirectory())) {
			final String[] suggestions = spellchecker.suggestSimilar(text, 5);
			final PDDocument document = new PDDocument();
			final PDPage page = new PDPage();
			document.addPage(page);

			final PDPageContentStream contentStream = new PDPageContentStream(document, page);

			contentStream.setFont(PDType1Font.COURIER, 12);
			contentStream.beginText();
			contentStream.showText(text);
			contentStream.endText();
			if (suggestions != null && suggestions.length > 0) {
				contentStream.beginText();
				contentStream.showText(Stream.of(suggestions).collect(Collectors.joining(",")));
				contentStream.endText();
			}
			contentStream.close();

			document.save(out);
			document.close();
		}
	}

}