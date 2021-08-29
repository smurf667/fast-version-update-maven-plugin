package com.example.simpleapp;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

public class Main {

	public static void main(final String args[]) throws IOException {
		if (args.length < 2) {
			System.out.printf("need <text> <filename.pdf>");
			return;
		}
		final PDDocument document = new PDDocument();
		final PDPage page = new PDPage();
		document.addPage(page);

		final PDPageContentStream contentStream = new PDPageContentStream(document, page);

		contentStream.setFont(PDType1Font.COURIER, 12);
		contentStream.beginText();
		contentStream.showText(args[0]);
		contentStream.endText();
		contentStream.close();

		document.save(args[1]);
		document.close();
	}

}
