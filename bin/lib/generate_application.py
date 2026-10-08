#!/usr/bin/env python3
"""Build a TE9/PE3 application payload from CCD case data and fill the PDF template."""

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
LOCATIONS = (
    "High Street car park",
    "Market Square",
    "Station Road",
    "Council offices forecourt",
    "Bus lane, Church Street",
    "Double yellow lines, Mill Lane",
)
COMPANIES = (
    "ACME LOGISTICS LTD",
    "NORTHSTAR FLEET SERVICES",
    "RIVERSIDE PARKING LTD",
    "",
    "",
)
HOW_PAID = ("Cash", "Cheque", "Debit card", "Credit card")
PAID_TO = (
    "Local authority",
    "Charging Authority",
    "Council payment office",
    "Bailiff",
)

TE9_DECLARATIONS = (
    "didNotReceivePcn",
    "madeRepresentationsNoRejection",
    "appealedToAdjudicator",
    "paidInFull",
)
PE3_DECLARATIONS = (
    "didNotReceiveNotice",
    "madeRepresentationsNoRejection",
    "appealedNoResponse",
)

# PE3 notice options under "I did not receive the:" — keep one, strike the others.
PE3_NOTICE_OPTIONS = (
    "noticeToOwner",
    "enforcementNotice",
    "penaltyChargeNotice",
)

PE3_REASON_SNIPPETS = (
    (
        "I did not receive the relevant notice and only learned of the charge when enforcement began.",
        "I ask that the order be set aside so I can challenge the penalty.",
    ),
    (
        "I made timely representations but received no rejection notice from the authority.",
        "I therefore could not appeal within the usual time limits.",
    ),
    (
        "I lodged an appeal with the adjudicator and have had no determination.",
        "It would be unjust for recovery to proceed while that appeal remains outstanding.",
    ),
    (
        "The details on the order do not match my records for this vehicle.",
        "I need the opportunity to put my case before any further enforcement.",
    ),
)

TE9_TITLE_EXPORT = {
    "Mr": ("Title - Mr", "/Mister"),
    "Mrs": ("Title - Mrs", "/Missus"),
    "Miss": ("Title - Miss", "/Miss"),
    "Ms": ("Title - Ms", "/Ms"),
    "Other": ("Title - Other", "/Other"),
}

TE9_DECLARATION_FIELDS = {
    "didNotReceivePcn": "did not receive the penalty charge notice - yes",
    "madeRepresentationsNoRejection": "representations but no rejection notice - yes",
    "appealedToAdjudicator": "no response to the appeal - yes",
    "paidInFull": "penalty charge has been paid in full - yes",
}

TE9_HOW_PAID_EXPORT = {
    "cash": ("Paid in cash", "/1"),
    "cheque": ("Paid by cheque", "/2"),
    "debit": ("Paid by debit card", "/3"),
    "credit": ("Paid by credit card", "/4"),
}

# Official TE9 overtype widget rectangles used to draw proper strike-through lines.
TE9_STRIKE_WITNESS_BELIEVES = (79.87, 164.79, 189.72, 178.39)
TE9_STRIKE_ON_BEHALF = (162.88, 111.43, 311.45, 124.40)
TE9_SIGNATURE_BOX = (124.62, 124.96, 345.82, 158.96)

POSTCODE_RE = re.compile(
    r"^[A-Z]{1,2}\d[A-Z\d]?\s*\d[A-Z]{2}$",
    re.IGNORECASE,
)

NAME_MISMATCH_RESULT = "Invalid - name does not match registration"
FIELDS_MISSING_RESULT = "Invalid - fields missing"
NOT_SIGNED_RESULT = "Invalid - application not signed"
MISMATCHED_NAME = "JORDAN UNRELATED"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case-json", required=True, help="Path to CCD case JSON or '-' for stdin")
    parser.add_argument("--type", required=True, choices=("inTime", "outOfTime"))
    parser.add_argument("--form", required=True, choices=("TE9", "PE3"))
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


def split_postcode(postcode: str) -> tuple[str, str]:
    parts = postcode.strip().upper().split()
    if len(parts) >= 2:
        return parts[0], parts[1]
    if len(postcode) > 3:
        return postcode[:-3].strip(), postcode[-3:].strip()
    return postcode, ""


def build_application(
    case_data: dict[str, Any],
    timeliness: str,
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

    date_received = date.today() - timedelta(days=rng.randint(0, 14))
    title = rng.choice(TITLES)
    company = rng.choice(COMPANIES)

    if form == "TE9":
        roll = rng.random()
        if roll < 0.1:
            selected_declarations: list[str] = []
        elif roll < 0.2:
            selected_declarations = rng.sample(list(TE9_DECLARATIONS), k=2)
        else:
            selected_declarations = [rng.choice(TE9_DECLARATIONS)]
    else:
        selected_declarations = (
            [] if rng.random() < 0.1 else [rng.choice(PE3_DECLARATIONS)]
        )

    declaration = selected_declarations[0] if selected_declarations else None

    payload: dict[str, Any] = {
        "applicationDateReceived": iso(date_received),
        "applicationType": timeliness,
        "applicationForm": form,
        "applicationPenaltyChargeNumber": case_data.get("penaltyChargeNumber"),
        "applicationVehicleRegistration": case_data.get("vehicleRegistrationNumber"),
        "applicationApplicant": name,
        "applicationLocationOfContravention": rng.choice(LOCATIONS),
        "applicationDateOfContravention": iso(date_of_contravention),
        "applicationTitle": title,
        "applicationFullName": name,
        "applicationCompanyName": company or None,
        "applicationAddress": ", ".join(address_parts) if address_parts else "1 EXAMPLE STREET, LONDON",
        "applicationPostcode": postcode,
        "applicationDeclaration": declaration,
        # PDF-only: may contain 0–2 TE9 tickboxes; stripped before CCD submit.
        "_pdfDeclarations": selected_declarations,
    }

    if timeliness == "outOfTime":
        payload["applicationTe7Submitted"] = rng.choice(("Yes", "No"))

    if form == "PE3":
        payload["applicationReasonsGiven"] = rng.choice(("Yes", "No"))
        payload["_pdfNoticeOption"] = rng.choice(PE3_NOTICE_OPTIONS)
        payload["_pdfReasonsText"] = "\n".join(rng.choice(PE3_REASON_SNIPPETS))

    if form == "TE9" and "paidInFull" in selected_declarations:
        payload["applicationDatePaid"] = iso(date_of_contravention + timedelta(days=rng.randint(1, 20)))
        payload["applicationHowPaid"] = rng.choice(HOW_PAID)
        payload["applicationPaidTo"] = rng.choice(PAID_TO)

    if validation_result == FIELDS_MISSING_RESULT:
        payload["applicationLocationOfContravention"] = None
        payload["applicationAddress"] = None
        payload["applicationDeclaration"] = None
        payload["_pdfDeclarations"] = []
    if validation_result == NOT_SIGNED_RESULT:
        payload["_pdfIncludeSignature"] = False
    if validation_result:
        payload["formValidationResultMessage"] = validation_result

    # Drop nulls so CCD optional fields stay omitted
    return {k: v for k, v in payload.items() if v is not None and v != ""}


def set_checkbox(writer: PdfWriter, page, field_name: str, on_value: str) -> None:
    """Set a checkbox/radio-style button to its on-state export value."""
    writer.update_page_form_field_values(page, {field_name: on_value}, auto_regenerate=False)


def draw_strike(c: canvas.Canvas, x0: float, y: float, x1: float, thickness: float = 1.2) -> None:
    c.setStrokeColor(black)
    c.setLineWidth(thickness)
    c.line(x0, y, x1, y)


def strike_rect(c: canvas.Canvas, rect: tuple[float, float, float, float]) -> None:
    x0, y0, x1, y1 = rect
    mid_y = (y0 + y1) / 2
    draw_strike(c, x0, mid_y, x1, thickness=1.4)


def draw_signature_squiggle(
    c: canvas.Canvas,
    rect: tuple[float, float, float, float],
) -> None:
    """Draw a simple ink-style squiggle inside a signature box."""
    x0, y0, x1, y1 = rect
    pad_x = min(12.0, (x1 - x0) * 0.08)
    pad_y = min(6.0, (y1 - y0) * 0.2)
    left = x0 + pad_x
    right = x1 - pad_x
    mid_y = (y0 + y1) / 2
    amp = max(3.0, (y1 - y0) * 0.28 - pad_y)

    c.setStrokeColor(black)
    c.setLineWidth(1.3)
    c.setLineCap(1)
    c.setLineJoin(1)
    path = c.beginPath()
    path.moveTo(left, mid_y - amp * 0.2)
    span = right - left
    path.curveTo(
        left + span * 0.18, mid_y + amp,
        left + span * 0.28, mid_y - amp,
        left + span * 0.42, mid_y + amp * 0.35,
    )
    path.curveTo(
        left + span * 0.55, mid_y + amp,
        left + span * 0.68, mid_y - amp * 1.1,
        left + span * 0.82, mid_y + amp * 0.45,
    )
    path.curveTo(
        left + span * 0.90, mid_y + amp * 0.8,
        left + span * 0.95, mid_y - amp * 0.4,
        right, mid_y + amp * 0.15,
    )
    c.drawPath(path, stroke=1, fill=0)


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


def fill_te9(template: Path, out_pdf: Path, payload: dict[str, Any]) -> None:
    """Fill the official TE9 AcroForm and draw strike-throughs for delete-as-appropriate clauses."""
    reader = PdfReader(str(template))
    writer = PdfWriter()
    writer.append(reader)
    writer.set_need_appearances_writer(True)
    page = writer.pages[0]

    title = payload.get("applicationTitle", "Mr")
    declarations = payload.get("_pdfDeclarations")
    if not isinstance(declarations, list):
        single = payload.get("applicationDeclaration")
        declarations = [single] if single else []
    how_paid = (payload.get("applicationHowPaid") or "").lower()
    postcode = payload.get("applicationPostcode") or ""
    outward, inward = split_postcode(postcode)

    text_values: dict[str, str] = {
        "Penalty charge number": payload.get("applicationPenaltyChargeNumber") or "",
        "Vehicle Registration No": payload.get("applicationVehicleRegistration") or "",
        "Applicant": payload.get("applicationApplicant") or "",
        "Location of Contravention": payload.get("applicationLocationOfContravention") or "",
        "Date of Contravention": payload.get("applicationDateOfContravention") or "",
        "Full name (witness)": payload.get("applicationFullName") or "",
        "Address": payload.get("applicationAddress") or "",
        "Postcode 1": outward,
        "Postcode 2": inward,
        "Company name if vehicle owned and registered by a company": payload.get("applicationCompanyName")
        or "",
        "Print full name": payload.get("applicationFullName") or "",
        "Date statement of truth signed": payload.get("applicationDateReceived") or "",
    }
    if "paidInFull" in declarations:
        text_values["date paid in full"] = payload.get("applicationDatePaid") or ""
        text_values["To whom was it paid"] = payload.get("applicationPaidTo") or ""

    writer.update_page_form_field_values(
        page,
        {k: v for k, v in text_values.items() if v},
        auto_regenerate=False,
    )

    title_field = TE9_TITLE_EXPORT.get(title)
    if title_field:
        set_checkbox(writer, page, title_field[0], title_field[1])

    for code in declarations:
        field = TE9_DECLARATION_FIELDS.get(code)
        if field:
            set_checkbox(writer, page, field, "/Yes")

    if "paidInFull" in declarations:
        for key, (field_name, on_value) in TE9_HOW_PAID_EXPORT.items():
            if key in how_paid:
                set_checkbox(writer, page, field_name, on_value)

    # Overlay strike lines and a signature squiggle; keep AcroForm fields intact.
    width = float(page.mediabox.width)
    height = float(page.mediabox.height)
    packet = BytesIO()
    c = canvas.Canvas(packet, pagesize=(width, height))
    # Always leave "(I believe)"; strike "(The witness believes)".
    # Below Signed: always strike "(person signing on behalf of the witness)".
    strike_rect(c, TE9_STRIKE_WITNESS_BELIEVES)
    strike_rect(c, TE9_STRIKE_ON_BEHALF)
    if payload.get("_pdfIncludeSignature", True):
        draw_signature_squiggle(c, TE9_SIGNATURE_BOX)
    c.save()
    packet.seek(0)
    page.merge_page(PdfReader(packet).pages[0])

    with out_pdf.open("wb") as handle:
        writer.write(handle)


def merge_overlay(template: Path, out_pdf: Path, draw) -> None:
    merge_overlay_bytes(template.read_bytes(), out_pdf, draw)


def draw_check_mark(c: canvas.Canvas, x: float, y: float, size: float = 9) -> None:
    c.setFont("Helvetica-Bold", size)
    c.setFillColor(black)
    c.drawString(x, y, "X")


def stamp_pe3(template: Path, out_pdf: Path, payload: dict[str, Any]) -> None:
    """Overlay values on the PE3 Word-export PDF using measured label positions.

    Coordinates are derived from pdftotext -bbox on the official PE3 layout
    (A4, origin bottom-left for drawing). The Declared at: section and below
    are left blank (no population or strike-outs).
    """

    declaration = payload.get("applicationDeclaration")
    notice_option = payload.get("_pdfNoticeOption") or "noticeToOwner"
    reasons_text = (payload.get("_pdfReasonsText") or "").strip()
    full_name = (payload.get("applicationFullName") or "").upper()
    address = (payload.get("applicationAddress") or "").upper()
    postcode = (payload.get("applicationPostcode") or "").upper()
    respondent_block = ", ".join(p for p in (full_name, address, postcode) if p)

    # Header value column sits to the right of the label column.
    value_x = 385.0
    header_fields = [
        (770.5, payload.get("applicationPenaltyChargeNumber") or ""),
        (756.0, payload.get("applicationVehicleRegistration") or ""),
        (741.5, payload.get("applicationApplicant") or ""),
        (727.0, payload.get("applicationLocationOfContravention") or ""),
        (713.0, payload.get("applicationDateOfContravention") or ""),
    ]

    checkbox_y = {
        "didNotReceiveNotice": 508.0,
        "madeRepresentationsNoRejection": 457.0,
        "appealedNoResponse": 419.0,
    }

    # Strike targets for the three notice lines (x0, pdf_y, x1)
    notice_strikes = {
        "noticeToOwner": (85.0, 495.5, 294.0),
        "enforcementNotice": (85.0, 482.5, 317.0),
        "penaltyChargeNotice": (85.0, 470.0, 532.0),
    }

    # Signed: box (left of Dated:), roughly measured from the Word-export layout.
    pe3_signature_box = (90.0, 242.0, 340.0, 262.0)

    def draw(c: canvas.Canvas, _width: float, _height: float) -> None:
        c.setFillColor(black)
        c.setFont("Helvetica", 9)
        for y, text in header_fields:
            if text:
                c.drawString(value_x, y, str(text)[:42])

        # Respondent name/address block under the "I, (full name..." instruction.
        if respondent_block:
            c.setFont("Helvetica", 9)
            c.drawString(50.0, 555.0, respondent_block[:95])

        if declaration in checkbox_y:
            draw_check_mark(c, 46.0, checkbox_y[declaration], size=10)

        # Strike the two notice options that do not apply when that declaration is used.
        if declaration == "didNotReceiveNotice":
            for key, (x0, y, x1) in notice_strikes.items():
                if key != notice_option:
                    draw_strike(c, x0, y, x1)

        # Always populate My reasons with two short sentences.
        if reasons_text:
            c.setFont("Helvetica", 8)
            lines = [line.strip() for line in reasons_text.splitlines() if line.strip()]
            if len(lines) == 1:
                words = lines[0].split()
                mid = max(1, len(words) // 2)
                lines = [" ".join(words[:mid]), " ".join(words[mid:])]
            c.drawString(50.0, 355.0, lines[0][:110])
            if len(lines) > 1 and lines[1]:
                c.drawString(50.0, 343.0, lines[1][:110])

        dated = payload.get("applicationDateReceived") or ""
        if dated:
            c.setFont("Helvetica", 9)
            # Sit in the Dated cell, clear of the "Dated:" label.
            c.drawString(400.0, 250.0, dated)

        if payload.get("_pdfIncludeSignature", True):
            draw_signature_squiggle(c, pe3_signature_box)

    merge_overlay(template, out_pdf, draw)


def main() -> None:
    args = parse_args()
    rng = random.Random(args.seed)
    case_data = load_case(args.case_json)
    payload = build_application(
        case_data,
        args.type,
        args.form,
        rng,
        args.form_validation_result,
    )

    args.out_pdf.parent.mkdir(parents=True, exist_ok=True)
    args.out_payload.parent.mkdir(parents=True, exist_ok=True)

    if args.form == "TE9":
        fill_te9(args.template, args.out_pdf, payload)
    else:
        stamp_pe3(args.template, args.out_pdf, payload)

    ccd_payload = {k: v for k, v in payload.items() if not str(k).startswith("_")}
    args.out_payload.write_text(json.dumps(ccd_payload, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"payloadPath": str(args.out_payload), "pdfPath": str(args.out_pdf)}))


if __name__ == "__main__":
    main()
