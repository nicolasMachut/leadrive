package fr.leadrevive.fixtures;

import fr.leadrevive.user.entity.User;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;

public final class UserFixture {

    private static final EasyRandom GENERATOR = new EasyRandom(new EasyRandomParameters());

    private UserFixture() {
    }

    public static User user() {
        return GENERATOR.nextObject(User.class);
    }

    public static User userAvecNom(final Long id, final String prenom, final String nom) {
        final User u = user();
        u.setId(id);
        u.setPrenom(prenom);
        u.setNom(nom);
        return u;
    }
}
