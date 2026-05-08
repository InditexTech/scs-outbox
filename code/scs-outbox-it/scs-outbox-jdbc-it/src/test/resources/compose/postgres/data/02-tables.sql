-- TABLES
CREATE TABLE IF NOT EXISTS
    SCS_OUTBOX
    (
    ID varchar(36) not null,
    BINDING_NAME varchar(256) not null ,
    CAPTURED_AT timestamp not null,
    DESTINATION varchar(256) not null,
    HEADERS text not null,
    PAYLOAD bytea not null,
    constraint PK_OUTBOX primary key (ID)
);
CREATE TABLE IF NOT EXISTS
  shedlock (
  name VARCHAR(64),
  lock_until TIMESTAMP(3) NULL,
  locked_at TIMESTAMP(3) NULL,
  locked_by VARCHAR(255),
  PRIMARY KEY (name)
);
CREATE TABLE IF NOT EXISTS
    SCS_OUTBOX_ARCHIVE
    (
    ID varchar(36) not null,
    ARCHIVED_AT timestamptz not null,
    CAPTURED_AT timestamptz not null,
    DESTINATION varchar(256) not null,
    CONTENT_TYPE varchar(256) not null,
    HEADERS text not null,
    PAYLOAD bytea not null,
    SERIALIZATION varchar(256) not null,
    JSON_PAYLOAD jsonb,
    constraint PK_OUTBOX_ARCHIVE primary key (ID)
);