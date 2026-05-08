CREATE TABLE IF NOT EXISTS
    SCS_OUTBOX_ARCHIVE
    (
    ID varchar(36) not null,
    ARCHIVED_AT timestamp not null,
    CAPTURED_AT timestamp not null,
    DESTINATION varchar(256) not null,
    CONTENT_TYPE varchar(256) not null,
    HEADERS text not null,
    PAYLOAD blob not null,
    SERIALIZATION varchar(256) not null,
    JSON_PAYLOAD text,
    constraint PK_OUTBOX_ARCHIVE primary key (ID)
);