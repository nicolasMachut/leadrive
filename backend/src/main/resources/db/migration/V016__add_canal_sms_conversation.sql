ALTER TABLE sms_conversation ADD COLUMN canal VARCHAR(20) DEFAULT 'WHATSAPP';
ALTER TABLE sms_conversation ALTER COLUMN numero_prospect DROP NOT NULL;
