from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any

CONFIDENCE_LEVELS = ("HIGH", "MEDIUM", "LOW", "REVIEW_REQUIRED")
SEVERITIES = ("FAIL", "WARN", "REVIEW_REQUIRED", "INFO")
AUDIENCES = ("PLAYER", "MODERATOR", "ADMIN", "TESTER", "DEVELOPER", "INTERNAL", "OUT_OF_SCOPE", "DEPRECATED", "CONSOLE")


@dataclass(frozen=True, order=True)
class Evidence:
    source: str
    line: int = 1
    symbol: str = ""
    detail: str = ""

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True, order=True)
class Finding:
    severity: str
    code: str
    message: str
    stable_id: str = ""
    evidence: tuple[Evidence, ...] = field(default_factory=tuple)

    def __post_init__(self) -> None:
        if self.severity not in SEVERITIES:
            raise ValueError(f"Unsupported severity: {self.severity}")

    def to_dict(self) -> dict[str, Any]:
        data = asdict(self)
        data["evidence"] = [item.to_dict() for item in self.evidence]
        return data


def finding_counts(findings: list[dict[str, Any]] | list[Finding]) -> dict[str, int]:
    counts = {name: 0 for name in SEVERITIES}
    for finding in findings:
        severity = finding.severity if isinstance(finding, Finding) else str(finding.get("severity", "INFO"))
        counts[severity] = counts.get(severity, 0) + 1
    return counts
