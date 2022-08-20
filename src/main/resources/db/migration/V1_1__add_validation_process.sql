ALTER TABLE training_process RENAME TO process;

ALTER TABLE process ADD COLUMN current_epoch BIGINT;
ALTER TABLE process ADD COLUMN DTYPE VARCHAR(255);
ALTER TABLE process ADD COLUMN validation_context CLOB;
ALTER TABLE process ADD COLUMN validation_result CLOB;
UPDATE process SET DTYPE = 'TrainingProcess';