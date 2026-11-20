ALTER TABLE sms_conversation ADD COLUMN mode_pilotage VARCHAR(10) DEFAULT 'IA_AUTO';
ALTER TABLE sms_conversation ADD COLUMN reprise_par_user_id BIGINT;
ALTER TABLE sms_conversation ADD COLUMN reprise_par_le TIMESTAMP;
ALTER TABLE sms_conversation ADD COLUMN derniere_lecture_le TIMESTAMP;
