package fr.leadrevive.csvimport.exception;

import lombok.Getter;

@Getter
public class CsvParsingException extends RuntimeException {

    public CsvParsingException(final String message) {
        super(message);
    }
}
