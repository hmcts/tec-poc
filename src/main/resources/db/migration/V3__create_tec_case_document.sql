create table tec_case_document (
    id uuid primary key default gen_random_uuid(),
    case_reference bigint not null references tec_case (case_reference),
    category_id varchar(100) not null,
    document_url varchar(1000) not null,
    document_binary_url varchar(1000) not null,
    filename varchar(500) not null,
    created_at timestamptz not null default now()
);

create index tec_case_document_case_reference_idx
    on tec_case_document (case_reference);
