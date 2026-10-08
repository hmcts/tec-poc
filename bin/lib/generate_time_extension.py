#!/usr/bin/env python3
"""Build a TE7/PE2 time-extension payload from CCD case data and fill the PDF template."""

from __future__ import annotations

import argparse
import json
import random
import re
import sys
from datetime import date, timedelta
from io import BytesIO
from pathlib import Path
from typing import Any

try:
    from pypdf import PdfReader, PdfWriter
except ImportError:  # pragma: no cover
    print(
        "Missing dependency: pypdf. Install with: python3 -m pip install pypdf reportlab",
        file=sys.stderr,
    )
    sys.exit(1)

try:
    from reportlab.lib.colors import black
    from reportlab.pdfgen import canvas
except ImportError:  # pragma: no cover
    print(
        "Missing dependency: reportlab. Install with: python3 -m pip install pypdf reportlab",
        file=sys.stderr,
    )
    sys.exit(1)

TITLES = ("Mr", "Mrs", "Miss", "Ms", "Other")
COMPANIES = (
    "ACME LOGISTICS LTD",
    "NORTHSTAR FLEET SERVICES",
    "RIVERSIDE PARKING LTD",
    "",
    "",
)
LOCATIONS = (
    "High Street car park",
    "Market Square",
    "Station Road",
    "Council offices forecourt",
    "Bus lane, Church Street",
)

TE7_PERMISSION = (
    ("outsideTheGivenTime", "outside the given time"),
    ("forMoreTime", "for more time"),
)
TE7_BELIEF = (
    ("iBelieve", "I believe"),
    ("theRespondentBelieves", "The respondent believes"),
)
TE7_SIGNED_BY = (
    ("respondent", None),
    ("officerOfTheCompany", ("An office of the company - yes", "/officer of the company")),
    ("partnerOfTheFirm", ("A Partner of the firm - yes", "/A Partner of the firm")),
    ("litigationFriend", ("Litigation friend - yes", "/Litigation friend")),
)
TE7_TITLE_EXPORT = {
    "Mr": ("Title - Mr", "/Mr"),
    "Mrs": ("Title - Mrs", "/Mrs"),
    "Miss": ("Title - Miss", "/Miss"),
    "Ms": ("Title - Ms", "/Ms"),
    "Other": ("Title - Other", "/Other"),
}

REASON_SNIPPETS = (
    (
        "I only received the enforcement papers after the deadline had passed.",
        "I need more time to gather evidence and file a statutory declaration.",
    ),
    (
        "I was away from home when the notice period expired.",
        "Please grant permission so I can put my case before the court.",
    ),
    (
        "I sought advice and could not complete the form in time.",
        "It would be unfair for recovery to continue without hearing my explanation.",
    ),
)

POSTCODE_RE = re.compile(
    r"^[A-Z]{1,2}\d[A-Z\d]?\s*\d[A-Z]{2}$",
    re.IGNORECASE,
)

NAME_MISMATCH_RESULT = "Invalid - name does not match registration"
FIELDS_MISSING_RESULT = "Invalid - fields missing"
NOT_SIGNED_RESULT = "Invalid - application not signed"
FOR_MORE_TIME_RESULT = (
    "Invalid - application is for more time, expecting application to file out of time"
)
MISMATCHED_NAME = "JORDAN UNRELATED"

TE7_SIGNATURE_BOX = (122.52, 164.24, 399.36, 193.04)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case-json", required=True, help="Path to CCD case JSON or '-' for stdin")
    parser.add_argument("--form", required=True, choices=("TE7", "PE2"))
    parser.add_argument("--template", required=True, type=Path)
    parser.add_argument("--out-pdf", required=True, type=Path)
    parser.add_argument("--out-payload", required=True, type=Path)
    parser.add_argument("--seed", type=int, default=None)
    parser.add_argument(
        "--form-validation-result",
        default=None,
        help="Replaces the default Form valid message stored on the form",
    )
    return parser.parse_args()


def load_case(path: str) -> dict[str, Any]:
    if path == "-":
        raw = sys.stdin.read()
    else:
        raw = Path(path).read_text(encoding="utf-8")
    case = json.loads(raw)
    data = case.get("data")
    if not isinstance(data, dict):
        raise SystemExit("CCD case JSON did not contain a data object")
    return data


def looks_like_postcode(value: str | None) -> bool:
    return bool(value and POSTCODE_RE.match(value.strip()))


def parse_certificate_date(value: str | None) -> date | None:
    if not value or not re.fullmatch(r"\d{6}", value):
        return None
    year = 2000 + int(value[0:2])
    month = int(value[2:4])
    day = int(value[4:6])
    try:
        return date(year, month, day)
    except ValueError:
        return None


def iso(d: date | None) -> str | None:
    return d.isoformat() if d else None


def set_checkbox(writer: PdfWriter, page, field_name: str, on_value: str) -> None:
    writer.update_page_form_field_values(page, {field_name: on_value}, auto_regenerate=False)


def draw_ink_signature(
    c: canvas.Canvas,
    rect: tuple[float, float, float, float],
) -> None:
    """Draw an illegible ink-style signature mark inside rect (not name-based)."""
    x0, y0, x1, y1 = rect
    pad_x = min(8.0, (x1 - x0) * 0.05)
    pad_y = min(2.0, (y1 - y0) * 0.12)
    left = x0 + pad_x
    right = x1 - pad_x
    bottom = y0 + pad_y
    top = y1 - pad_y
    mid_y = (bottom + top) / 2
    span = max(right - left, 1.0)
    amp = max(3.0, (top - bottom) * 0.48)

    c.setStrokeColor(black)
    c.setLineCap(1)
    c.setLineJoin(1)

    # Opening loop (like a hurried capital).
    c.setLineWidth(1.45)
    lead = c.beginPath()
    lead.moveTo(left, mid_y - amp * 0.4)
    lead.curveTo(
        left + span * 0.02, top,
        left + span * 0.08, top,
        left + span * 0.11, mid_y,
    )
    lead.curveTo(
        left + span * 0.13, bottom,
        left + span * 0.08, bottom,
        left + span * 0.15, mid_y + amp * 0.15,
    )
    c.drawPath(lead, stroke=1, fill=0)

    # Main illegible run with tall peaks and troughs.
    c.setLineWidth(1.25)
    main = c.beginPath()
    main.moveTo(left + span * 0.15, mid_y + amp * 0.15)
    main.curveTo(
        left + span * 0.22, top,
        left + span * 0.26, bottom,
        left + span * 0.33, mid_y + amp * 0.55,
    )
    main.curveTo(
        left + span * 0.38, top,
        left + span * 0.44, bottom - amp * 0.2,
        left + span * 0.52, mid_y - amp * 0.2,
    )
    main.curveTo(
        left + span * 0.58, top,
        left + span * 0.64, bottom,
        left + span * 0.72, mid_y + amp * 0.65,
    )
    main.curveTo(
        left + span * 0.78, top,
        left + span * 0.84, bottom + amp * 0.1,
        left + span * 0.90, mid_y,
    )
    c.drawPath(main, stroke=1, fill=0)

    # Overlapping thinner stroke to thicken like wet ink.
    c.setLineWidth(0.7)
    ghost = c.beginPath()
    ghost.moveTo(left + span * 0.18, mid_y)
    ghost.curveTo(
        left + span * 0.30, bottom,
        left + span * 0.45, top,
        left + span * 0.60, mid_y - amp * 0.1,
    )
    ghost.curveTo(
        left + span * 0.72, bottom,
        left + span * 0.82, top,
        left + span * 0.92, mid_y + amp * 0.2,
    )
    c.drawPath(ghost, stroke=1, fill=0)

    # Rising terminal slash / flourish.
    c.setLineWidth(1.15)
    end = c.beginPath()
    end.moveTo(left + span * 0.86, bottom + amp * 0.1)
    end.curveTo(
        left + span * 0.92, mid_y,
        left + span * 0.96, top,
        right, top - amp * 0.15,
    )
    c.drawPath(end, stroke=1, fill=0)

    # Short underline tick.
    c.setLineWidth(0.9)
    under = c.beginPath()
    under.moveTo(left + span * 0.48, bottom + amp * 0.05)
    under.curveTo(
        left + span * 0.62, bottom - amp * 0.15,
        left + span * 0.78, bottom + amp * 0.25,
        left + span * 0.88, bottom + amp * 0.05,
    )
    c.drawPath(under, stroke=1, fill=0)


def draw_signature_squiggle(
    c: canvas.Canvas,
    rect: tuple[float, float, float, float],
) -> None:
    draw_ink_signature(c, rect)


def merge_overlay_bytes(pdf_bytes: bytes, out_pdf: Path, draw) -> None:
    reader = PdfReader(BytesIO(pdf_bytes))
    page = reader.pages[0]
    width = float(page.mediabox.width)
    height = float(page.mediabox.height)

    packet = BytesIO()
    c = canvas.Canvas(packet, pagesize=(width, height))
    draw(c, width, height)
    c.save()
    packet.seek(0)
    overlay = PdfReader(packet)

    writer = PdfWriter()
    page.merge_page(overlay.pages[0])
    writer.add_page(page)
    for extra in reader.pages[1:]:
        writer.add_page(extra)

    with out_pdf.open("wb") as handle:
        writer.write(handle)


def merge_overlay(template: Path, out_pdf: Path, draw) -> None:
    merge_overlay_bytes(template.read_bytes(), out_pdf, draw)


def build_time_extension(
    case_data: dict[str, Any],
    form: str,
    rng: random.Random,
    form_validation_result: str | None = None,
) -> dict[str, Any]:
    validation_result = (form_validation_result or "").strip() or None
    name = (case_data.get("respondentDetails1") or "ALEX EXAMPLE").strip()
    if validation_result == NAME_MISMATCH_RESULT:
        name = MISMATCHED_NAME
    line2 = (case_data.get("respondentDetails2") or "").strip()
    line3 = (case_data.get("respondentDetails3") or "").strip()
    line4 = (case_data.get("respondentDetails4") or "").strip()
    line5 = (case_data.get("respondentDetails5") or "").strip()
    line6 = (case_data.get("respondentDetails6") or "").strip()

    address_parts = [p for p in (line2, line3) if p]
    postcode = None
    if looks_like_postcode(line4):
        postcode = line4.upper()
    else:
        if line4:
            address_parts.append(line4)
        for candidate in (line5, line6):
            if looks_like_postcode(candidate):
                postcode = candidate.upper()
            elif candidate:
                address_parts.append(candidate)
        if postcode is None:
            postcode = rng.choice(("SW1A 1AA", "NN1 2LH", "M1 1AE", "B1 1BB"))

    certificate_date = parse_certificate_date(case_data.get("dateChargeCertificateServed"))
    if certificate_date:
        date_of_contravention = certificate_date - timedelta(days=rng.randint(14, 90))
    else:
        date_of_contravention = date.today() - timedelta(days=rng.randint(30, 180))

    date_signed = date.today() - timedelta(days=rng.randint(0, 10))
    title = rng.choice(TITLES)
    company = rng.choice(COMPANIES)
    reasons_text = "\n".join(rng.choice(REASON_SNIPPETS))
    reasons_given = "Yes" if rng.random() >= 0.1 else "No"
    include_signature = rng.random() >= 0.05
    include_date = rng.random() >= 0.05
    if validation_result == NOT_SIGNED_RESULT:
        include_signature = False
        include_date = False
    signed_and_dated = "Yes" if include_signature and include_date else "No"

    payload: dict[str, Any] = {
        "timeExtensionForm": form,
        "timeExtensionPenaltyChargeNumber": case_data.get("penaltyChargeNumber"),
        "timeExtensionVehicleRegistration": case_data.get("vehicleRegistrationNumber"),
        "timeExtensionFullName": name,
        "timeExtensionAddress": ", ".join(address_parts) if address_parts else "1 EXAMPLE STREET, LONDON",
        "timeExtensionPostcode": postcode,
        "timeExtensionReasonsGiven": reasons_given,
        "timeExtensionSignedAndDated": signed_and_dated,
        "timeExtensionDateSigned": iso(date_signed) if include_date else None,
        "_pdfReasonsText": reasons_text if reasons_given == "Yes" else "",
        "_pdfIncludeSignature": include_signature,
    }

    if form == "TE7":
        permission_code, permission_label = rng.choice(TE7_PERMISSION)
        if validation_result == FOR_MORE_TIME_RESULT:
            permission_code, permission_label = "forMoreTime", "for more time"
        _, belief_label = rng.choice(TE7_BELIEF)
        signed_by_code, capacity = rng.choice(TE7_SIGNED_BY)
        # Prefer respondent (~70%); otherwise one of the capacity checkboxes.
        if rng.random() < 0.7:
            signed_by_code, capacity = TE7_SIGNED_BY[0]

        payload.update(
            {
                "timeExtensionTitle": title,
                "timeExtensionOtherTitle": "Dr" if title == "Other" else None,
                "timeExtensionCompanyName": company or None,
                "timeExtensionPermissionType": permission_code,
                "timeExtensionSignedBy": signed_by_code,
                "timeExtensionPrintFullName": name,
                "_pdfPermissionLabel": permission_label,
                "_pdfBeliefLabel": belief_label,
                "_pdfSignedByCapacity": capacity,
            }
        )
    else:
        payload.update(
            {
                "timeExtensionApplicant": name,
                "timeExtensionLocationOfContravention": rng.choice(LOCATIONS),
                "timeExtensionDateOfContravention": iso(date_of_contravention),
            }
        )

    if validation_result == FIELDS_MISSING_RESULT:
        payload["timeExtensionAddress"] = None
        payload["timeExtensionReasonsGiven"] = "No"
        payload["_pdfReasonsText"] = ""
    if validation_result:
        payload["formValidationResultMessage"] = validation_result

    return {k: v for k, v in payload.items() if v is not None and v != ""}


def fill_te7(template: Path, out_pdf: Path, payload: dict[str, Any]) -> None:
    reader = PdfReader(str(template))
    writer = PdfWriter()
    writer.append(reader)
    writer.set_need_appearances_writer(True)
    page = writer.pages[0]

    title = payload.get("timeExtensionTitle", "Mr")
    text_values: dict[str, str] = {
        "Penalty Charge No": payload.get("timeExtensionPenaltyChargeNumber") or "",
        "Vehicle Registration No": payload.get("timeExtensionVehicleRegistration") or "",
        "Full name Respondent": payload.get("timeExtensionFullName") or "",
        "Respondent's address": payload.get("timeExtensionAddress") or "",
        "Respondent's postcode": payload.get("timeExtensionPostcode") or "",
        "Company name if vehicle owned and registered by a company": payload.get(
            "timeExtensionCompanyName"
        )
        or "",
        "Print full name": payload.get("timeExtensionPrintFullName") or "",
        "Date of signature": payload.get("timeExtensionDateSigned") or "",
        "Reasons for applying for permission": payload.get("_pdfReasonsText") or "",
    }
    if title == "Other":
        text_values["Other title"] = payload.get("timeExtensionOtherTitle") or "Dr"

    writer.update_page_form_field_values(
        page,
        {k: v for k, v in text_values.items() if v},
        auto_regenerate=False,
    )

    title_field = TE7_TITLE_EXPORT.get(title)
    if title_field:
        set_checkbox(writer, page, title_field[0], title_field[1])

    permission_label = payload.get("_pdfPermissionLabel") or "outside the given time"
    belief_label = payload.get("_pdfBeliefLabel") or "I believe"
    writer.update_page_form_field_values(
        page,
        {
            "outside the given time/for more time (choose an option)": permission_label,
            "I believe/The respondent believes (choose an option)": belief_label,
        },
        auto_regenerate=False,
    )

    signed_by = payload.get("timeExtensionSignedBy") or "respondent"
    capacity = payload.get("_pdfSignedByCapacity")
    if signed_by == "respondent" or not capacity:
        writer.update_page_form_field_values(
            page,
            {
                "Respondent/Person signing on behalf of the respondent (choose an option)": "Respondent",
            },
            auto_regenerate=False,
        )
    else:
        writer.update_page_form_field_values(
            page,
            {
                "Respondent/Person signing on behalf of the respondent (choose an option)": (
                    "Person signing on behalf of respondent"
                ),
            },
            auto_regenerate=False,
        )
        set_checkbox(writer, page, capacity[0], capacity[1])

    include_signature = bool(payload.get("_pdfIncludeSignature", True))
    if include_signature:
        width = float(page.mediabox.width)
        height = float(page.mediabox.height)
        packet = BytesIO()
        c = canvas.Canvas(packet, pagesize=(width, height))
        draw_signature_squiggle(c, TE7_SIGNATURE_BOX)
        c.save()
        packet.seek(0)
        overlay_pages = PdfReader(packet).pages
        if overlay_pages:
            page.merge_page(overlay_pages[0])

    with out_pdf.open("wb") as handle:
        writer.write(handle)


def stamp_pe2(template: Path, out_pdf: Path, payload: dict[str, Any]) -> None:
    """Overlay PE2 values; leave Declared at: and below blank."""
    reasons_text = (payload.get("_pdfReasonsText") or "").strip()
    full_name = (payload.get("timeExtensionFullName") or "").upper()
    address = (payload.get("timeExtensionAddress") or "").upper()
    postcode = (payload.get("timeExtensionPostcode") or "").upper()
    respondent_block = ", ".join(p for p in (full_name, address, postcode) if p)
    include_signature = bool(payload.get("_pdfIncludeSignature", True))

    value_x = 385.0
    header_fields = [
        (772.0, payload.get("timeExtensionPenaltyChargeNumber") or ""),
        (757.0, payload.get("timeExtensionVehicleRegistration") or ""),
        (743.0, payload.get("timeExtensionApplicant") or ""),
        (728.0, payload.get("timeExtensionLocationOfContravention") or ""),
        (714.0, payload.get("timeExtensionDateOfContravention") or ""),
    ]
    # Signed:/Dated: row — labels near top of cell; write in the lower-middle of the box.
    # Box roughly pdf y 262–282 (page height 841.92; Signed: ~565 from top).
    pe2_signature_box = (108.0, 261.8, 348.0, 274.8)
    pe2_dated_xy = (400.0, 266.5)

    def draw(c: canvas.Canvas, _width: float, _height: float) -> None:
        c.setFillColor(black)
        c.setFont("Helvetica", 9)
        for y, text in header_fields:
            if text:
                c.drawString(value_x, y, str(text)[:42])

        if respondent_block:
            c.drawString(50.0, 580.0, respondent_block[:95])

        if reasons_text:
            c.setFont("Helvetica", 8)
            lines = [line.strip() for line in reasons_text.splitlines() if line.strip()]
            y = 490.0
            for line in lines[:4]:
                c.drawString(50.0, y, line[:105])
                y -= 12.0

        dated = payload.get("timeExtensionDateSigned") or ""
        if dated:
            c.setFont("Helvetica", 9)
            c.drawString(pe2_dated_xy[0], pe2_dated_xy[1], dated)

        if include_signature:
            draw_ink_signature(c, pe2_signature_box)

    merge_overlay(template, out_pdf, draw)


def main() -> None:
    args = parse_args()
    rng = random.Random(args.seed)
    case_data = load_case(args.case_json)
    payload = build_time_extension(
        case_data,
        args.form,
        rng,
        args.form_validation_result,
    )

    args.out_pdf.parent.mkdir(parents=True, exist_ok=True)
    args.out_payload.parent.mkdir(parents=True, exist_ok=True)

    if args.form == "TE7":
        fill_te7(args.template, args.out_pdf, payload)
    else:
        stamp_pe2(args.template, args.out_pdf, payload)

    ccd_payload = {k: v for k, v in payload.items() if not str(k).startswith("_")}
    args.out_payload.write_text(json.dumps(ccd_payload, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"payloadPath": str(args.out_payload), "pdfPath": str(args.out_pdf)}))


if __name__ == "__main__":
    main()
