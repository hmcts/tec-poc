create table tec_case_note (
    id uuid primary key default gen_random_uuid(),
    case_reference bigint not null references tec_case (case_reference),
    created_on timestamptz not null,
    created_by varchar(255) not null,
    note varchar(500) not null
);

create index tec_case_note_case_reference_idx
    on tec_case_note (case_reference);
