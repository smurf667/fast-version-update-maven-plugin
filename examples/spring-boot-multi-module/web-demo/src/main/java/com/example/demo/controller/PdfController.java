package com.example.demo.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.library.Generator;

@RestController
public class PdfController {

	@GetMapping(value = "generate/{text}")
	public void getFileByName(@PathVariable("text") final String text, final HttpServletResponse response) throws IOException {
		response.setContentType(MediaType.APPLICATION_PDF_VALUE);
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"demo.pdf\"");
		Generator.generatePDF(text, response.getOutputStream());
	}
}
