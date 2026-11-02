"""Verify published JSON output SPI access and implementation encapsulation on JPMS."""
from pathlib import Path
import subprocess
import sys
import tempfile

base = Path(__file__).resolve().parents[2]
jar = base / "util/json/target/hibernate-search-util-json-9.0.0-SNAPSHOT.jar"
module_path = str(jar) + ":" + Path(sys.argv[1]).read_text().strip()
with tempfile.TemporaryDirectory(prefix="json-task3-jpms-") as directory:
    directory = Path(directory)
    source = directory / "src"
    (source / "smoke").mkdir(parents=True)
    (source / "module-info.java").write_text("module smoke { requires org.hibernate.search.util.json; }\n")
    main = source / "smoke/Main.java"
    main.write_text(r'''package smoke;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonBodies;
import org.hibernate.search.util.json.spi.JsonBody;
import org.hibernate.search.util.json.spi.JsonWriteCursor;
public class Main {
 public static void main(String[] args) throws Exception {
  JsonObject tree = new JsonObject();
  tree.addProperty("value", "<é😀\ud800");
  JsonBody body = JsonBodies.of(tree);
  ByteArrayOutputStream synchronous = new ByteArrayOutputStream();
  body.writeTo(synchronous);
  ByteArrayOutputStream incremental = new ByteArrayOutputStream();
  try (JsonWriteCursor cursor = body.openCursor()) {
   ByteBuffer buffer = ByteBuffer.allocateDirect(1);
   while (!cursor.isComplete()) {
    cursor.write(buffer);
    buffer.flip();
    incremental.write(buffer.get());
    buffer.clear();
   }
  }
  try (var signing = body.openStream()) {
   if (!Arrays.equals(synchronous.toByteArray(), signing.readAllBytes())) throw new AssertionError("signing bytes");
  }
  if (!Arrays.equals(synchronous.toByteArray(), incremental.toByteArray())) throw new AssertionError("cursor bytes");
  if (body.contentLength() != synchronous.size()) throw new AssertionError("length");
 }
}
''')
    output = directory / "classes"
    arguments = ["javac", "--release", "17", "--module-path", module_path,
                 "-d", str(output), str(source / "module-info.java"), str(main)]
    subprocess.run(arguments, check=True)
    subprocess.run(["java", "--module-path", module_path + ":" + str(output), "-m", "smoke/smoke.Main"], check=True)
    print("Public output SPI named-module compile/runtime and signing byte equality: PASS", flush=True)
    for implementation in ["Utf8JsonWriteCursor", "JacksonJsonBodyWriter"]:
        main.write_text("package smoke; import org.hibernate.search.util.json.impl." + implementation
                        + "; public class Main { " + implementation + " implementation; }")
        rejected = subprocess.run(arguments, text=True, capture_output=True)
        if rejected.returncode == 0 or "does not export" not in rejected.stderr:
            raise AssertionError(rejected.stderr)
        print("External import rejected: " + implementation + ": PASS", flush=True)
        print(rejected.stderr.strip(), flush=True)
    subprocess.run(["jar", "--describe-module", "--file", str(jar)], check=True)
