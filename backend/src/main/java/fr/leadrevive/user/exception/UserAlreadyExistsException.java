package fr.leadrevive.user.exception;

import lombok.Getter;

@Getter
public class UserAlreadyExistsException extends RuntimeException {

    private final String email;

    public UserAlreadyExistsException(final String email) {
        super("Utilisateur deja existant - email: " + email);
        this.email = email;
    }
}
