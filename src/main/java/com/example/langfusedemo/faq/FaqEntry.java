package com.example.langfusedemo.faq;

import java.util.List;

public record FaqEntry(String id, String category, List<String> keywords, String question, String answer) {
}
