package fr.leadrevive.user.exception;

import lombok.Getter;

@Getter
public class UserNotFoundException extends RuntimeException {

    private final Long userId;

    public UserNotFoundException(final Long userId) {
        super("Utilisateur non trouve - id: " + userId);
        this.userId = userId;
    }
}
