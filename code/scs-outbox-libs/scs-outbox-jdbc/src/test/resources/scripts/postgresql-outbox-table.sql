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
