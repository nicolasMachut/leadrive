-- Suppression des conversations de simulation et de leurs messages associes
DELETE FROM sms_message WHERE conversation_id IN (
    SELECT conversation_id FROM sms_conversation WHERE canal = 'SIMULATION'
);
DELETE FROM sms_conversation WHERE canal = 'SIMULATION';
