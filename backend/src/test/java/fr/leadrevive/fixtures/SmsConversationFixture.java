package fr.leadrevive.fixtures;

import fr.leadrevive.outreach.entity.ModePilotageEnum;
import fr.leadrevive.outreach.entity.SmsConversation;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;

public final class SmsConversationFixture {

    private static final EasyRandom GENERATOR = new EasyRandom(new EasyRandomParameters());

    private SmsConversationFixture() {
    }

    public static SmsConversation conversation() {
        final SmsConversation conv = GENERATOR.nextObject(SmsConversation.class);
        conv.setModePilotage(ModePilotageEnum.IA_AUTO);
        return conv;
    }

    public static SmsConversation conversationAvecMode(final String conversationId,
                                                      final Long tenantId,
                                                      final ModePilotageEnum mode) {
        final SmsConversation conv = conversation();
        conv.setConversationId(conversationId);
        conv.setTenantId(tenantId);
        conv.setModePilotage(mode);
        return conv;
    }
}
