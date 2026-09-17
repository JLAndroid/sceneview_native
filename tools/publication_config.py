"""Shared publication coordinates; does not invoke Gradle or read credentials."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROPERTIES = dict(
    line.split('=', 1) for line in (ROOT/'gradle.properties').read_text('utf-8').splitlines()
    if '=' in line and not line.lstrip().startswith(('#', '!'))
)
GROUP = PROPERTIES['GROUP'].strip()
VERSION = PROPERTIES['VERSION_NAME'].strip()
GROUP_PATH = Path(*GROUP.split('.'))
