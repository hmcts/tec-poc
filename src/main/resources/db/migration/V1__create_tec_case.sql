create table tec_case (
    case_reference bigint primary key,
    file_identifier varchar(9) not null,
    batch_identifier varchar(10) not null,
    penalty_charge_number varchar(12) not null,
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
    registration_date timestamp with time zone,
    constraint tec_case_registration_request_uk unique (
        file_identifier,
        batch_identifier,
        penalty_charge_number
    ),
    constraint tec_case_file_identifier_ck check (
        file_identifier ~ '^R[A-Z]{2,3}[0-9]{5}$'
    ),
    constraint tec_case_batch_identifier_ck check (
        batch_identifier ~ '^R[A-Z]{2,3}[0-9]{6}$'
    ),
    constraint tec_case_batch_prefix_ck check (
        substring(file_identifier from 2 for char_length(file_identifier) - 6)
        = substring(batch_identifier from 2 for char_length(batch_identifier) - 7)
    ),
    constraint tec_case_pcn_ck check (
        penalty_charge_number ~ '^[A-Z]{2,3}[0-9]{7}[0-9A][0-9]$'
    ),
    constraint tec_case_pcn_prefix_ck check (
        substring(penalty_charge_number from 1 for char_length(penalty_charge_number) - 9)
        = substring(file_identifier from 2 for char_length(file_identifier) - 6)
    ),
    constraint tec_case_respondent_1_ck check (
        char_length(btrim(respondent_details_1)) > 0
        and respondent_details_1 = upper(respondent_details_1)
    ),
    constraint tec_case_respondent_2_ck check (
        char_length(btrim(respondent_details_2)) > 0
        and respondent_details_2 = upper(respondent_details_2)
    ),
    constraint tec_case_respondent_3_ck check (
        char_length(btrim(respondent_details_3)) > 0
        and respondent_details_3 = upper(respondent_details_3)
    ),
    constraint tec_case_respondent_4_ck check (
        respondent_details_4 is null
        or respondent_details_4 = upper(respondent_details_4)
    ),
    constraint tec_case_respondent_5_ck check (
        respondent_details_5 is null
        or respondent_details_5 = upper(respondent_details_5)
    ),
    constraint tec_case_respondent_6_ck check (
        respondent_details_6 is null
        or respondent_details_6 = upper(respondent_details_6)
    ),
    constraint tec_case_vrn_ck check (
        vehicle_registration_number ~ '^[A-Z0-9]+$'
    ),
    constraint tec_case_offence_ck check (
        nature_of_offence ~ '^[0-9]{2}$'
    ),
    constraint tec_case_certificate_date_ck check (
        date_charge_certificate_served ~ '^[0-9]{6}$'
    ),
    constraint tec_case_amount_due_ck check (
        amount_due >= 0 and amount_due <= 999999
    )
);
