#!/usr/bin/env python3
"""Run the stock REST5 cancellation probe using a matching Failsafe classpath."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

project = Path(__file__).resolve().parents[3]
reports = project / "integrationtest/backend/elasticsearch/target/failsafe-reports/elasticsearch-rest5"
report = next(reports.glob("TEST-*ElasticsearchJsonTransportIT*.xml"), None)
if report is None:
    raise SystemExit("Run ElasticsearchJsonTransportIT in @it-elasticsearch-rest5-client first.")
properties = ET.parse(report).getroot().find("properties")
classpath = next(p.attrib["value"] for p in properties if p.attrib["name"] == "java.class.path")
source = Path(__file__).with_name("Task6CancelProbe.java")
with tempfile.TemporaryDirectory(prefix="task6-cancel-probe-") as classes:
    subprocess.run(["javac", "--release", "17", "-cp", classpath, "-d", classes, str(source)], check=True)
    subprocess.run(["java", "-cp", classes + ":" + classpath, "Task6CancelProbe"], check=True)
