create table tec_case_general_application (
    id uuid primary key default gen_random_uuid(),
    case_reference bigint not null references tec_case (case_reference),
    rank integer not null,
    applicant varchar(50) not null,
    date_received date not null,
    application_type varchar(50) not null,
    something_else_details text,
    within_14_days varchar(10),
    fee_amount_received integer not null,
    applied_for_hwf varchar(10) not null,
    hwf_reference varchar(60),
    all_parties_agree varchar(10) not null,
    without_notice varchar(10),
    state varchar(50) not null,
    created_at timestamptz not null default now(),
    constraint tec_case_general_application_rank_uk unique (case_reference, rank)
);

create index tec_case_general_application_case_reference_idx
    on tec_case_general_application (case_reference);
