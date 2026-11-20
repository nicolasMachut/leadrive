package fr.leadrevive.csvimport.exception;

import lombok.Getter;

@Getter
public class ImportNotFoundException extends RuntimeException {

    private final Long importId;

    public ImportNotFoundException(final Long importId) {
        super("Import non trouve - id: " + importId);
        this.importId = importId;
    }
}
