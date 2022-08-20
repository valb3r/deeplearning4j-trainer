CREATE TABLE dataset
(
    id                                 BIGINT AUTO_INCREMENT NOT NULL,
    name                               VARCHAR(255),
    process_id                         BIGINT,
    CONSTRAINT pk_dataset PRIMARY KEY (id)
);

ALTER TABLE dataset ADD FOREIGN KEY (process_id) REFERENCES process(id);



CREATE TABLE dataset_file
(
    id                                 BIGINT AUTO_INCREMENT NOT NULL,
    path                               VARCHAR(1024),
    name                               VARCHAR(255),
    orig_hash                          VARCHAR(255),
    dataset_id                         BIGINT                NOT NULL,
    CONSTRAINT pk_dataset_file PRIMARY KEY (id)
);

ALTER TABLE dataset_file ADD FOREIGN KEY (dataset_id) REFERENCES dataset(id);