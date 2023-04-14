CREATE TABLE event_tag (
    "event_id" BIGINT NOT NULL,
    "tag" NVARCHAR(255) NOT NULL
    PRIMARY KEY ("event_id","tag")
    constraint "fk_event_journal"
        foreign key("event_id")
        references "dbo"."event_journal"("ordering")
        on delete CASCADE
);
