CREATE SEQUENCE deeplearning4j_trainer_sequence INCREMENT BY 50;

CREATE TABLE training_process
(
    id                                 BIGINT NOT NULL,
    trained_model_path                 VARCHAR(255),
    business_key                       VARCHAR(255),
    process_definition_name            VARCHAR(255),
    process_id                         VARCHAR(255),
    training_context                   BYTEA,
    current_iter                       BIGINT,
    best_performing_epoch              BIGINT,
    best_performing_trained_model_path VARCHAR(255),
    best_loss                          REAL,
    error_stacktrace                   BYTEA,
    error_message                      VARCHAR(4096),
    notes                              VARCHAR(4096),
    completed                          BOOLEAN               NOT NULL,
    updated_at                         TIMESTAMP,
    CONSTRAINT pk_trainingprocess PRIMARY KEY (id)
);

ALTER TABLE training_process
    ADD CONSTRAINT uc_trainingprocess_processid UNIQUE (process_id);