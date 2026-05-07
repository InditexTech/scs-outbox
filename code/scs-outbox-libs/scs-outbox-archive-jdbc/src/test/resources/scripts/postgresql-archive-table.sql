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