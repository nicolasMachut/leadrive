package fr.leadrevive.fixtures;

import fr.leadrevive.outreach.entity.ConversationAnalyse;
import fr.leadrevive.outreach.entity.IntentionEnum;

import java.util.List;

public final class ConversationAnalyseFixture {

    private ConversationAnalyseFixture() {
    }

    public static ConversationAnalyse analyse(final IntentionEnum intention,
                                              final List<String> signaux,
                                              final List<String> objections,
                                              final boolean handoff) {
        return analyse(intention, signaux, objections, handoff, false);
    }

    public static ConversationAnalyse analyse(final IntentionEnum intention,
                                              final List<String> signaux,
                                              final List<String> objections,
                                              final boolean handoff,
                                              final boolean rdvConfirme) {
        final ConversationAnalyse a = new ConversationAnalyse();
        a.setIntention(intention);
        a.setSignauxAchat(signaux);
        a.setObjections(objections);
        a.setBesoinHandoff(handoff);
        a.setRdvConfirme(rdvConfirme);
        return a;
    }

    public static ConversationAnalyse analyseNeutre() {
        return analyse(IntentionEnum.AUTRE, null, null, false);
    }
}
