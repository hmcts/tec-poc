-- PCN stem stays on the case. Registration and each form type move to their own tables.

alter table tec_case
    add column pcn_stem varchar(11);

update tec_case
   set pcn_stem = left(penalty_charge_number, char_length(penalty_charge_number) - 1);

alter table tec_case
    alter column pcn_stem set not null;

alter table tec_case
    add constraint tec_case_pcn_stem_ck check (
        pcn_stem ~ '^[A-Z]{2,3}[0-9]{7}[0-9A]$'
    );

create table tec_case_registration (
    id uuid primary key default gen_random_uuid(),
    case_reference bigint not null references tec_case (case_reference),
    file_identifier varchar(9) not null,
    batch_identifier varchar(10) not null,
    batch_case_reference bigint references tec_batch (case_reference),
    penalty_charge_number varchar(12) not null,
    local_authority varchar(100) not null,
    respondent_details_1 varchar(30) not null,
    respondent_details_2 varchar(30) not null,
    respondent_details_3 varchar(30) not null,
    respondent_details_4 varchar(30),
    respondent_details_5 varchar(30),
    respondent_details_6 varchar(30),
    vehicle_registration_number varchar(10) not null,
    nature_of_offence char(2) not null,
    date_charge_certificate_served char(6) not null,
    amount_due integer not null,
    payment_status varchar(30) not null default 'PENDING',
    payment_reference varchar(100),
    closure_reason varchar(100),
    registration_document varchar(500),
    registration_date date,
    created_at timestamptz not null default now(),
    constraint tec_case_registration_pcn_uk unique (penalty_charge_number),
    constraint tec_case_registration_file_identifier_ck check (
        file_identifier ~ '^R[A-Z]{2,3}[0-9]{5}$'
    ),
    constraint tec_case_registration_batch_identifier_ck check (
        batch_identifier ~ '^R[A-Z]{2,3}[0-9]{6}$'
    ),
    constraint tec_case_registration_batch_prefix_ck check (
        substring(file_identifier from 2 for char_length(file_identifier) - 6)
        = substring(batch_identifier from 2 for char_length(batch_identifier) - 7)
    ),
    constraint tec_case_registration_pcn_ck check (
        penalty_charge_number ~ '^[A-Z]{2,3}[0-9]{7}[0-9A][0-9]$'
    ),
    constraint tec_case_registration_pcn_prefix_ck check (
        substring(penalty_charge_number from 1 for char_length(penalty_charge_number) - 9)
        = substring(file_identifier from 2 for char_length(file_identifier) - 6)
    ),
    constraint tec_case_registration_respondent_1_ck check (
        char_length(btrim(respondent_details_1)) > 0
        and respondent_details_1 = upper(respondent_details_1)
    ),
    constraint tec_case_registration_respondent_2_ck check (
        char_length(btrim(respondent_details_2)) > 0
        and respondent_details_2 = upper(respondent_details_2)
    ),
    constraint tec_case_registration_respondent_3_ck check (
        char_length(btrim(respondent_details_3)) > 0
        and respondent_details_3 = upper(respondent_details_3)
    ),
    constraint tec_case_registration_respondent_4_ck check (
        respondent_details_4 is null
        or respondent_details_4 = upper(respondent_details_4)
    ),
    constraint tec_case_registration_respondent_5_ck check (
        respondent_details_5 is null
        or respondent_details_5 = upper(respondent_details_5)
    ),
    constraint tec_case_registration_respondent_6_ck check (
        respondent_details_6 is null
        or respondent_details_6 = upper(respondent_details_6)
    ),
    constraint tec_case_registration_vrn_ck check (
        vehicle_registration_number ~ '^[A-Z0-9]+$'
    ),
    constraint tec_case_registration_offence_ck check (
        nature_of_offence ~ '^[0-9]{2}$'
    ),
    constraint tec_case_registration_certificate_date_ck check (
        date_charge_certificate_served ~ '^[0-9]{6}$'
    ),
    constraint tec_case_registration_amount_due_ck check (
        amount_due >= 0 and amount_due <= 999999
    )
);

create index tec_case_registration_case_reference_idx
    on tec_case_registration (case_reference);

create index tec_case_registration_batch_case_reference_idx
    on tec_case_registration (batch_case_reference);

insert into tec_case_registration (
    case_reference, file_identifier, batch_identifier, batch_case_reference,
    penalty_charge_number, local_authority,
    respondent_details_1, respondent_details_2, respondent_details_3,
    respondent_details_4, respondent_details_5, respondent_details_6,
    vehicle_registration_number, nature_of_offence,
    date_charge_certificate_served, amount_due,
    payment_status, payment_reference, closure_reason,
    registration_document, registration_date
)
select
    case_reference, file_identifier, batch_identifier, batch_case_reference,
    penalty_charge_number, local_authority,
    respondent_details_1, respondent_details_2, respondent_details_3,
    respondent_details_4, respondent_details_5, respondent_details_6,
    vehicle_registration_number, nature_of_offence,
    date_charge_certificate_served, amount_due,
    payment_status, payment_reference, closure_reason,
    registration_document, registration_date::date
from tec_case;

create table tec_case_te9 (
    id uuid primary key default gen_random_uuid(),
    case_reference bigint not null references tec_case (case_reference),
    penalty_charge_number varchar(12) not null,
    date_received date,
    type varchar(30),
    te7_submitted varchar(10),
    vehicle_registration varchar(10),
    applicant varchar(200),
    location_of_contravention varchar(500),
    date_of_contravention date,
    title varchar(50),
    full_name varchar(200),
    company_name varchar(200),
    address varchar(500),
    postcode varchar(20),
    declaration varchar(50),
    date_paid date,
    how_paid varchar(100),
    paid_to varchar(200),
    created_at timestamptz not null default now(),
    constraint tec_case_te9_pcn_ck check (
        penalty_charge_number ~ '^[A-Z]{2,3}[0-9]{7}[0-9A][0-9]$'
    )
);

create index tec_case_te9_case_reference_idx on tec_case_te9 (case_reference);

insert into tec_case_te9 (
    case_reference, penalty_charge_number, date_received, type, te7_submitted,
    vehicle_registration, applicant, location_of_contravention, date_of_contravention,
    title, full_name, company_name, address, postcode, declaration,
    date_paid, how_paid, paid_to
)
select
    case_reference, application_penalty_charge_number, application_date_received,
    application_type, application_te7_submitted, application_vehicle_registration,
    application_applicant, application_location_of_contravention,
    application_date_of_contravention, application_title, application_full_name,
    application_company_name, application_address, application_postcode,
    application_declaration, application_date_paid, application_how_paid,
    application_paid_to
from tec_case
where application_form = 'TE9'
  and application_penalty_charge_number is not null;

create table tec_case_pe3 (
    id uuid primary key default gen_random_uuid(),
    case_reference bigint not null references tec_case (case_reference),
    penalty_charge_number varchar(12) not null,
    date_received date,
    type varchar(30),
    te7_submitted varchar(10),
    vehicle_registration varchar(10),
    applicant varchar(200),
    location_of_contravention varchar(500),
    date_of_contravention date,
    title varchar(50),
    full_name varchar(200),
    company_name varchar(200),
    address varchar(500),
    postcode varchar(20),
    declaration varchar(50),
    reasons_given varchar(10),
    date_paid date,
    how_paid varchar(100),
    paid_to varchar(200),
    created_at timestamptz not null default now(),
    constraint tec_case_pe3_pcn_ck check (
        penalty_charge_number ~ '^[A-Z]{2,3}[0-9]{7}[0-9A][0-9]$'
    )
);

create index tec_case_pe3_case_reference_idx on tec_case_pe3 (case_reference);

insert into tec_case_pe3 (
    case_reference, penalty_charge_number, date_received, type, te7_submitted,
    vehicle_registration, applicant, location_of_contravention, date_of_contravention,
    title, full_name, company_name, address, postcode, declaration, reasons_given,
    date_paid, how_paid, paid_to
)
select
    case_reference, application_penalty_charge_number, application_date_received,
    application_type, application_te7_submitted, application_vehicle_registration,
    application_applicant, application_location_of_contravention,
    application_date_of_contravention, application_title, application_full_name,
    application_company_name, application_address, application_postcode,
    application_declaration, application_reasons_given, application_date_paid,
    application_how_paid, application_paid_to
from tec_case
where application_form = 'PE3'
  and application_penalty_charge_number is not null;

create table tec_case_te7 (
    id uuid primary key default gen_random_uuid(),
    case_reference bigint not null references tec_case (case_reference),
    penalty_charge_number varchar(12) not null,
    vehicle_registration varchar(10),
    title varchar(50),
    other_title varchar(50),
    full_name varchar(200),
    company_name varchar(200),
    address varchar(500),
    postcode varchar(20),
    permission_type varchar(40),
    reasons_given varchar(10),
    signed_and_dated varchar(10),
    signed_by varchar(40),
    date_signed date,
    print_full_name varchar(200),
    created_at timestamptz not null default now(),
    constraint tec_case_te7_pcn_ck check (
        penalty_charge_number ~ '^[A-Z]{2,3}[0-9]{7}[0-9A][0-9]$'
    )
);

create index tec_case_te7_case_reference_idx on tec_case_te7 (case_reference);

insert into tec_case_te7 (
    case_reference, penalty_charge_number, vehicle_registration, title, other_title,
    full_name, company_name, address, postcode, permission_type, reasons_given,
    signed_and_dated, signed_by, date_signed, print_full_name
)
select
    case_reference, time_extension_penalty_charge_number,
    time_extension_vehicle_registration, time_extension_title, time_extension_other_title,
    time_extension_full_name, time_extension_company_name, time_extension_address,
    time_extension_postcode, time_extension_permission_type, time_extension_reasons_given,
    time_extension_signed_and_dated, time_extension_signed_by, time_extension_date_signed,
    time_extension_print_full_name
from tec_case
where time_extension_form = 'TE7'
  and time_extension_penalty_charge_number is not null;

create table tec_case_pe2 (
    id uuid primary key default gen_random_uuid(),
    case_reference bigint not null references tec_case (case_reference),
    penalty_charge_number varchar(12) not null,
    vehicle_registration varchar(10),
    applicant varchar(200),
    location_of_contravention varchar(500),
    date_of_contravention date,
    full_name varchar(200),
    address varchar(500),
    postcode varchar(20),
    reasons_given varchar(10),
    signed_and_dated varchar(10),
    date_signed date,
    created_at timestamptz not null default now(),
    constraint tec_case_pe2_pcn_ck check (
        penalty_charge_number ~ '^[A-Z]{2,3}[0-9]{7}[0-9A][0-9]$'
    )
);

create index tec_case_pe2_case_reference_idx on tec_case_pe2 (case_reference);

insert into tec_case_pe2 (
    case_reference, penalty_charge_number, vehicle_registration, applicant,
    location_of_contravention, date_of_contravention, full_name, address, postcode,
    reasons_given, signed_and_dated, date_signed
)
select
    case_reference, time_extension_penalty_charge_number,
    time_extension_vehicle_registration, time_extension_applicant,
    time_extension_location_of_contravention, time_extension_date_of_contravention,
    time_extension_full_name, time_extension_address, time_extension_postcode,
    time_extension_reasons_given, time_extension_signed_and_dated,
    time_extension_date_signed
from tec_case
where time_extension_form = 'PE2'
  and time_extension_penalty_charge_number is not null;

alter table tec_case drop constraint tec_case_registration_request_uk;
alter table tec_case drop constraint tec_case_file_identifier_ck;
alter table tec_case drop constraint tec_case_batch_identifier_ck;
alter table tec_case drop constraint tec_case_batch_prefix_ck;
alter table tec_case drop constraint tec_case_pcn_ck;
alter table tec_case drop constraint tec_case_pcn_prefix_ck;
alter table tec_case drop constraint tec_case_respondent_4_ck;
alter table tec_case drop constraint tec_case_respondent_5_ck;
alter table tec_case drop constraint tec_case_respondent_6_ck;
alter table tec_case drop constraint tec_case_offence_ck;
alter table tec_case drop constraint tec_case_certificate_date_ck;
alter table tec_case drop constraint tec_case_amount_due_ck;
alter table tec_case drop constraint tec_case_batch_case_fk;

drop index tec_case_batch_case_reference_idx;

alter table tec_case
    drop column file_identifier,
    drop column batch_identifier,
    drop column penalty_charge_number,
    drop column respondent_details_4,
    drop column respondent_details_5,
    drop column respondent_details_6,
    drop column nature_of_offence,
    drop column date_charge_certificate_served,
    drop column amount_due,
    drop column payment_status,
    drop column payment_reference,
    drop column closure_reason,
    drop column registration_document,
    drop column registration_date,
    drop column batch_case_reference,
    drop column application_date_received,
    drop column application_type,
    drop column application_te7_submitted,
    drop column application_form,
    drop column application_penalty_charge_number,
    drop column application_vehicle_registration,
    drop column application_applicant,
    drop column application_location_of_contravention,
    drop column application_date_of_contravention,
    drop column application_title,
    drop column application_full_name,
    drop column application_company_name,
    drop column application_address,
    drop column application_postcode,
    drop column application_declaration,
    drop column application_reasons_given,
    drop column application_date_paid,
    drop column application_how_paid,
    drop column application_paid_to,
    drop column time_extension_form,
    drop column time_extension_penalty_charge_number,
    drop column time_extension_vehicle_registration,
    drop column time_extension_applicant,
    drop column time_extension_location_of_contravention,
    drop column time_extension_date_of_contravention,
    drop column time_extension_title,
    drop column time_extension_other_title,
    drop column time_extension_full_name,
    drop column time_extension_company_name,
    drop column time_extension_address,
    drop column time_extension_postcode,
    drop column time_extension_permission_type,
    drop column time_extension_reasons_given,
    drop column time_extension_signed_and_dated,
    drop column time_extension_signed_by,
    drop column time_extension_date_signed,
    drop column time_extension_print_full_name;
